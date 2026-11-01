package com.exambattle.tools;

import com.exambattle.common.io.BoundedLineReader;
import com.exambattle.common.io.MessageWriter;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageCodec;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.payload.ChatBroadcast;
import com.exambattle.common.protocol.payload.ChatRequest;
import com.exambattle.common.protocol.payload.HelloPayload;
import com.exambattle.common.protocol.payload.JoinRoomRequest;
import com.exambattle.common.protocol.payload.LoginRequest;
import com.exambattle.common.protocol.payload.RegisterRequest;
import com.exambattle.common.protocol.payload.RoomState;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * 多客戶端同時連線測試：
 * <ol>
 *   <li>N 個客戶端同時連線、以隨機前綴的帳號註冊並登入</li>
 *   <li>前 R 個客戶端各建立一個房間，其餘依序平均加入</li>
 *   <li>全部進房後每人送 M 則聊天</li>
 *   <li>檢查每個人只收到同房的全部訊息，且同房的人看到的順序一致，最後送 BYE</li>
 * </ol>
 * 註冊的帳號會留在資料庫中，帳號以 {@code lt_} 開頭，清除方式見 docs/TESTING.md。
 */
public final class MultiClientLoadTest {

    private static final String PASSWORD = "load-test-password";
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";

    /**
     * @param host              伺服器位址
     * @param port              伺服器埠號
     * @param clients           同時連線的客戶端數量
     * @param rooms             房間數量（1 ≤ rooms ≤ clients）
     * @param messagesPerClient 每個客戶端送出的聊天數
     * @param timeout           整個測試的時間上限
     */
    public record Options(String host, int port, int clients, int rooms, int messagesPerClient, Duration timeout) {

        public Options {
            if (clients < 1 || rooms < 1 || rooms > clients) {
                throw new IllegalArgumentException("需滿足 1 ≤ rooms ≤ clients");
            }
        }
    }

    /** 測試結果。 */
    public record Result(int clients, int succeeded, int rooms, boolean sameOrderInEveryRoom,
                         Duration elapsed, List<String> failures) {

        public boolean passed() {
            return succeeded == clients && sameOrderInEveryRoom && failures.isEmpty();
        }
    }

    private final Options options;
    private final String runPrefix;

    public MultiClientLoadTest(Options options) {
        this.options = options;
        this.runPrefix = "lt_" + randomTag(6) + "_";
    }

    public Result run() throws InterruptedException {
        int n = options.clients();
        int timeoutMillis = Math.toIntExact(options.timeout().toMillis());
        CyclicBarrier loggedIn = new CyclicBarrier(n);
        CyclicBarrier roomsCreated = new CyclicBarrier(n);
        CyclicBarrier everyoneSeated = new CyclicBarrier(n);
        AtomicReferenceArray<String> codes = new AtomicReferenceArray<>(options.rooms());
        long start = System.nanoTime();

        List<Future<List<String>>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < n; i++) {
                int index = i;
                futures.add(pool.submit(() -> runClient(index, codes, loggedIn, roomsCreated, everyoneSeated,
                        timeoutMillis)));
            }

            List<String> failures = new ArrayList<>();
            List<List<String>> received = new ArrayList<>();
            long deadline = start + options.timeout().toNanos();
            for (int i = 0; i < n; i++) {
                try {
                    long remaining = Math.max(1, deadline - System.nanoTime());
                    received.add(futures.get(i).get(remaining, TimeUnit.NANOSECONDS));
                } catch (ExecutionException e) {
                    received.add(null);
                    failures.add("client-" + i + "：" + e.getCause());
                } catch (TimeoutException e) {
                    received.add(null);
                    failures.add("client-" + i + "：逾時");
                    futures.get(i).cancel(true);
                }
            }
            boolean sameOrder = true;
            int succeeded = 0;
            for (int r = 0; r < options.rooms(); r++) {
                List<String> first = null;
                for (int i = r; i < n; i += options.rooms()) {
                    List<String> texts = received.get(i);
                    if (texts == null) {
                        continue;
                    }
                    succeeded++;
                    if (first == null) {
                        first = texts;
                    } else if (!first.equals(texts)) {
                        sameOrder = false;
                    }
                }
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - start);
            return new Result(n, succeeded, options.rooms(), sameOrder, elapsed, failures);
        }
    }

    /** 第 index 個客戶端：房間編號為 index % rooms，index < rooms 的是房主。 */
    private List<String> runClient(int index, AtomicReferenceArray<String> codes, CyclicBarrier loggedIn,
                                   CyclicBarrier roomsCreated, CyclicBarrier everyoneSeated, int timeoutMillis)
            throws Exception {
        int room = index % options.rooms();
        boolean owner = index < options.rooms();
        int roomSize = (options.clients() - room + options.rooms() - 1) / options.rooms();
        String username = runPrefix + index;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(options.host(), options.port()), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);
            Connection c = new Connection(socket);

            c.request(MessageType.HELLO, new HelloPayload(Protocol.VERSION), MessageType.WELCOME);
            c.request(MessageType.REGISTER, new RegisterRequest(username, PASSWORD, "Load " + index),
                    MessageType.REGISTER_OK);
            c.request(MessageType.LOGIN, new LoginRequest(username, PASSWORD), MessageType.LOGIN_OK);
            loggedIn.await(timeoutMillis, TimeUnit.MILLISECONDS);

            if (owner) {
                codes.set(room, c.request(MessageType.CREATE_ROOM, null, MessageType.ROOM_STATE)
                        .payloadAs(RoomState.class).roomCode());
            }
            roomsCreated.await(timeoutMillis, TimeUnit.MILLISECONDS);
            String code = codes.get(room);
            if (!owner) {
                c.request(MessageType.JOIN_ROOM, new JoinRoomRequest(code), MessageType.ROOM_STATE);
            }
            everyoneSeated.await(timeoutMillis, TimeUnit.MILLISECONDS);

            for (int k = 0; k < options.messagesPerClient(); k++) {
                c.send(MessageType.CHAT, new ChatRequest(username + "#" + k));
            }
            int expected = roomSize * options.messagesPerClient();
            List<String> texts = new ArrayList<>(expected);
            while (texts.size() < expected) {
                ChatBroadcast chat = c.nextOfType(MessageType.CHAT).payloadAs(ChatBroadcast.class);
                if (!code.equals(chat.roomCode())) {
                    throw new IOException("收到其他範圍的聊天：" + chat);
                }
                texts.add(chat.text());
            }
            c.send(MessageType.BYE, null);
            return texts;
        }
    }

    private static String randomTag(int length) {
        SecureRandom random = new SecureRandom();
        StringBuilder tag = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            tag.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return tag.toString();
    }

    /** 同步收送的最小連線。 */
    private static final class Connection {

        private final BoundedLineReader in;
        private final MessageWriter out;
        private int nextRequest = 1;

        Connection(Socket socket) throws IOException {
            in = new BoundedLineReader(
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)),
                    Protocol.MAX_LINE_CHARS);
            out = new MessageWriter(new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)));
        }

        String send(MessageType type, Object payload) throws IOException {
            String requestId = "lt-" + nextRequest++;
            out.write(Message.of(type, requestId, payload));
            return requestId;
        }

        /** 送出請求並等待對應的回應；收到 ERROR 視為失敗。 */
        Message request(MessageType type, Object payload, MessageType expected) throws Exception {
            String requestId = send(type, payload);
            while (true) {
                Message m = next();
                if (requestId.equals(m.requestId())) {
                    if (!m.is(expected)) {
                        throw new IOException(type + " 失敗：" + MessageCodec.encode(m));
                    }
                    return m;
                }
            }
        }

        /** 讀到指定類型為止（略過上線通知等其他訊息）；收到 ERROR 視為失敗。 */
        Message nextOfType(MessageType type) throws Exception {
            while (true) {
                Message m = next();
                if (m.is(type)) {
                    return m;
                }
                if (m.is(MessageType.ERROR)) {
                    throw new IOException("收到 ERROR：" + MessageCodec.encode(m));
                }
            }
        }

        private Message next() throws Exception {
            String line = in.readLine();
            if (line == null) {
                throw new IOException("伺服器關閉了連線");
            }
            return MessageCodec.decode(line);
        }
    }
}
