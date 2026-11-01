package com.exambattle.client.net;

import com.exambattle.common.io.BoundedLineReader;
import com.exambattle.common.io.MessageWriter;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageCodec;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.ProtocolException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 客戶端網路層：連線、送出訊息、在背景執行緒接收訊息並交給 {@link Listener}。
 * 與介面無關，日後換成 GUI 也能沿用。
 *
 * <p>收到伺服器的 PING 會自動回 PONG，不會轉交給 Listener。
 * 以 {@link #request} 送出的請求會依 requestId 對應回應：所有訊息都先交給 Listener，
 * 再完成對應的 future（因此狀態先更新，再通知呼叫端）。
 */
public final class ServerConnection implements AutoCloseable {

    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;

    /** 接收端回呼；在背景執行緒中被呼叫。 */
    public interface Listener {
        void onMessage(Message message);

        /** 連線被伺服器關閉或中斷；呼叫 {@link #close()} 主動關閉時不會觸發。 */
        void onDisconnected(String reason);

        /** 收到無法解析的行。 */
        default void onBadMessage(String line, String reason) {
        }
    }

    private final Socket socket;
    private final MessageWriter writer;
    private final Listener listener;
    private final AtomicLong nextRequestNumber = new AtomicLong(1);
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Map<String, CompletableFuture<Message>> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "heartbeat");
        t.setDaemon(true);
        return t;
    });

    private ServerConnection(Socket socket, Listener listener) throws IOException {
        this.socket = socket;
        this.listener = listener;
        this.writer = new MessageWriter(new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)));
    }

    /** 連線並開始在背景接收訊息。 */
    public static ServerConnection connect(String host, int port, Listener listener) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
            socket.setTcpNoDelay(true);
            ServerConnection connection = new ServerConnection(socket, listener);
            Thread reader = new Thread(connection::readLoop, "server-reader");
            reader.setDaemon(true);
            reader.start();
            return connection;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    /** 送出訊息並回傳產生的 requestId。 */
    public String send(MessageType type, Object payload) throws IOException {
        String requestId = nextRequestId();
        write(Message.of(type, requestId, payload));
        return requestId;
    }

    /**
     * 送出請求並等待帶相同 requestId 的回應。伺服器回 ERROR 時以 {@link ServerErrorException} 結束；
     * 連線中斷時以 IOException 結束。future 在讀取執行緒上完成。
     */
    public CompletableFuture<Message> request(MessageType type, Object payload) {
        String requestId = nextRequestId();
        CompletableFuture<Message> future = new CompletableFuture<>();
        pending.put(requestId, future);
        try {
            write(Message.of(type, requestId, payload));
        } catch (IOException e) {
            pending.remove(requestId);
            future.completeExceptionally(e);
        }
        return future;
    }

    private String nextRequestId() {
        return "c-" + nextRequestNumber.getAndIncrement();
    }

    /** 依指定間隔自動送出 PING，讓伺服器知道連線仍存在。 */
    public void startHeartbeat(Duration interval) {
        long millis = interval.toMillis();
        heartbeat.scheduleAtFixedRate(() -> {
            try {
                send(MessageType.PING, null);
            } catch (IOException e) {
                // 讀取端會偵測到斷線並通知 Listener
            }
        }, millis, millis, TimeUnit.MILLISECONDS);
    }

    /** 送出 BYE 後關閉連線。 */
    public void sendByeAndClose() {
        try {
            send(MessageType.BYE, null);
        } catch (IOException ignored) {
            // 已斷線，直接關閉
        }
        close();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            heartbeat.shutdownNow();
            failPending("連線已關閉");
            try {
                socket.close();
            } catch (IOException ignored) {
                // 已無法處理
            }
        }
    }

    private synchronized void write(Message message) throws IOException {
        writer.write(message);
    }

    private void readLoop() {
        String reason = "伺服器關閉了連線";
        try {
            BoundedLineReader reader = new BoundedLineReader(
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)),
                    Protocol.MAX_LINE_CHARS);
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    dispatch(line);
                }
            }
        } catch (IOException e) {
            reason = "連線中斷：" + e.getMessage();
        }
        if (closed.compareAndSet(false, true)) {
            heartbeat.shutdownNow();
            failPending(reason);
            try {
                socket.close();
            } catch (IOException ignored) {
                // 已無法處理
            }
            listener.onDisconnected(reason);
        }
    }

    private void failPending(String reason) {
        IOException error = new IOException(reason);
        pending.values().forEach(f -> f.completeExceptionally(error));
        pending.clear();
    }

    private void dispatch(String line) throws IOException {
        Message message;
        try {
            message = MessageCodec.decode(line);
        } catch (ProtocolException e) {
            listener.onBadMessage(line, e.getMessage());
            return;
        }
        if (message.is(MessageType.PING)) {
            write(Message.empty(MessageType.PONG, message.requestId()));
            return;
        }
        listener.onMessage(message);
        CompletableFuture<Message> future = message.requestId() == null ? null : pending.remove(message.requestId());
        if (future != null) {
            if (message.is(MessageType.ERROR)) {
                future.completeExceptionally(ServerErrorException.from(message));
            } else {
                future.complete(message);
            }
        }
    }
}
