package com.exambattle.server;

import com.exambattle.common.io.BoundedLineReader;
import com.exambattle.common.io.LineTooLongException;
import com.exambattle.common.io.MessageWriter;
import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageCodec;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.ProtocolException;
import com.exambattle.common.protocol.payload.ByePayload;
import com.exambattle.common.protocol.payload.ErrorPayload;
import com.exambattle.common.protocol.payload.HelloPayload;
import com.exambattle.common.protocol.payload.LeaveReason;
import com.exambattle.common.protocol.payload.UserInfo;
import com.exambattle.common.protocol.payload.WelcomePayload;
import com.exambattle.server.handler.PayloadValidator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 處理單一客戶端連線。
 *
 * <p>{@link #run()} 在讀取執行緒中逐行解析並處理訊息；送出的訊息先放進佇列，
 * 由獨立的寫入執行緒依序寫出，所以廣播時不會被單一慢速客戶端阻塞。
 */
final class ClientHandler implements Runnable, Session {

    private static final Logger LOG = Logger.getLogger(ClientHandler.class.getName());

    /** 放進佇列表示「送完前面的訊息後關閉連線」；以參考相等判斷。 */
    private static final Message CLOSE_SIGNAL = Message.empty(MessageType.BYE, null);
    private static final int DRAIN_TIMEOUT_MILLIS = 500;
    private static final long MAX_DRAIN_BYTES = 1L << 20;

    private final Socket socket;
    private final String clientId;
    private final ServerContext context;
    private final Duration idleTimeout;
    private final BlockingQueue<Message> outbox = new LinkedBlockingQueue<>();

    /** 只由讀取執行緒寫入。 */
    private volatile boolean welcomed;
    /** 登入前為 null；由 ClientRegistry 在鎖內寫入。 */
    private volatile UserInfo user;
    private volatile boolean closing;
    private volatile boolean readerDone;

    ClientHandler(Socket socket, String clientId, ServerContext context, Duration idleTimeout) {
        this.socket = socket;
        this.clientId = clientId;
        this.context = context;
        this.idleTimeout = idleTimeout;
    }

    @Override
    public String clientId() {
        return clientId;
    }

    @Override
    public UserInfo user() {
        return user;
    }

    @Override
    public void bindUser(UserInfo user) {
        this.user = user;
        LOG.info(() -> "登入 " + describe());
    }

    /** 將訊息排入送出佇列；連線關閉中則忽略。可由任何執行緒呼叫。 */
    @Override
    public void send(Message message) {
        if (!closing) {
            outbox.add(message);
        }
    }

    /** 送完佇列中的訊息後關閉連線。 */
    void closeAfterFlush() {
        closing = true;
        outbox.add(CLOSE_SIGNAL);
    }

    /** 伺服器關閉時呼叫：通知客戶端後斷線。 */
    void shutdown() {
        send(Message.of(MessageType.BYE, null, new ByePayload(ByePayload.SERVER_SHUTDOWN)));
        closeAfterFlush();
    }

    @Override
    public void run() {
        Thread.ofVirtual().name("writer-" + clientId).start(this::writeLoop);
        LeaveReason reason = LeaveReason.DISCONNECTED;
        try {
            socket.setSoTimeout(Math.toIntExact(idleTimeout.toMillis()));
            BoundedLineReader reader = new BoundedLineReader(
                    new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)),
                    Protocol.MAX_LINE_CHARS);
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank() && handleLine(line)) {
                    reason = LeaveReason.BYE;
                    break;
                }
            }
        } catch (LineTooLongException e) {
            sendError(null, ErrorCode.LINE_TOO_LONG, e.getMessage());
        } catch (SocketTimeoutException e) {
            reason = LeaveReason.TIMEOUT;
        } catch (IOException e) {
            // 對方斷線，或伺服器關閉時 socket 已被關閉
        } finally {
            readerDone = true;
            context.disconnect(this, reason);
            closeAfterFlush();
            LeaveReason finalReason = reason;
            LOG.info(() -> "連線結束 " + describe() + "，原因 " + finalReason);
        }
    }

    /** 處理一行訊息；回傳 true 表示客戶端送出 BYE。 */
    private boolean handleLine(String line) {
        Message message;
        try {
            message = MessageCodec.decode(line);
        } catch (ProtocolException e) {
            sendError(e);
            return false;
        }

        Optional<MessageType> type = MessageType.fromWire(message.type())
                .filter(MessageType::isClientSendable);
        if (type.isEmpty()) {
            sendError(message.requestId(), ErrorCode.UNKNOWN_TYPE, "不支援的訊息類型: " + message.type());
            return false;
        }

        try {
            switch (type.get()) {
                case HELLO -> onHello(message);
                case PING -> send(Message.empty(MessageType.PONG, message.requestId()));
                case PONG -> {
                    // 心跳回應；讀到任何資料時 socket 逾時已重新計算，不需額外處理
                }
                case BYE -> {
                    return true;
                }
                case REGISTER -> {
                    requireWelcomed(message);
                    context.auth().register(this, message);
                }
                case LOGIN -> {
                    requireWelcomed(message);
                    context.auth().login(this, message);
                }
                case CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM, GET_ROOM -> {
                    requireLoggedIn(message);
                    context.roomHandler().handle(this, type.get(), message);
                }
                case CHAT -> {
                    requireLoggedIn(message);
                    context.chat().handle(this, message);
                }
                case LIST_SUBJECTS, CREATE_SUBJECT, LIST_EXAMS, GET_EXAM, CREATE_EXAM, UPDATE_EXAM, DELETE_EXAM,
                     ADD_QUESTION, UPDATE_QUESTION, DELETE_QUESTION, REORDER_QUESTIONS, IMPORT_QUESTIONS -> {
                    requireLoggedIn(message);
                    context.bank().handle(this, type.get(), message);
                }
                case START_PRACTICE, ANSWER_QUESTION, SUBMIT_PRACTICE, LIST_RECORDS, GET_RECORD, LIST_WRONG,
                     REMOVE_WRONG -> {
                    requireLoggedIn(message);
                    context.practice().handle(this, type.get(), message);
                }
                default -> throw new IllegalStateException("未處理的訊息類型: " + type.get());
            }
        } catch (ProtocolException e) {
            sendError(e);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "處理訊息失敗 " + describe(), e);
            sendError(message.requestId(), ErrorCode.INTERNAL, "伺服器內部錯誤");
        }
        return false;
    }

    private void onHello(Message message) throws ProtocolException {
        if (welcomed) {
            throw new ProtocolException(ErrorCode.ALREADY_WELCOMED, "已經完成 HELLO", message.requestId());
        }
        PayloadValidator.hello(message.payloadAs(HelloPayload.class), message.requestId());
        welcomed = true;
        send(Message.of(MessageType.WELCOME, message.requestId(), new WelcomePayload(clientId, Protocol.VERSION)));
    }

    private void requireWelcomed(Message message) throws ProtocolException {
        if (!welcomed) {
            throw new ProtocolException(ErrorCode.NOT_HELLO, "請先送出 HELLO", message.requestId());
        }
    }

    private void requireLoggedIn(Message message) throws ProtocolException {
        requireWelcomed(message);
        if (user == null) {
            throw new ProtocolException(ErrorCode.NOT_LOGGED_IN, "請先登入", message.requestId());
        }
    }

    private void sendError(ProtocolException e) {
        sendError(e.requestId(), e.code(), e.getMessage());
    }

    private void sendError(String requestId, ErrorCode code, String text) {
        send(Message.of(MessageType.ERROR, requestId, new ErrorPayload(code, text)));
    }

    private void writeLoop() {
        try (BufferedWriter out = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            MessageWriter writer = new MessageWriter(out);
            Message message;
            while ((message = outbox.take()) != CLOSE_SIGNAL) {
                writer.write(message);
            }
        } catch (IOException e) {
            // 對方已斷線；讀取端會收到錯誤並完成離線流程
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            closing = true;
            closeGracefully();
        }
    }

    /**
     * 先送出 FIN 再關閉。若讀取端已結束（例如單行過長），先丟棄對方尚未讀取的資料，
     * 否則直接關閉會送出 RST，對方可能收不到最後的 ERROR。
     */
    private void closeGracefully() {
        try {
            socket.shutdownOutput();
            if (readerDone) {
                socket.setSoTimeout(DRAIN_TIMEOUT_MILLIS);
                InputStream in = socket.getInputStream();
                byte[] buffer = new byte[8192];
                long drained = 0;
                int n;
                while (drained < MAX_DRAIN_BYTES && (n = in.read(buffer)) != -1) {
                    drained += n;
                }
            }
        } catch (IOException ignored) {
            // 對方已斷線或逾時，直接關閉
        } finally {
            try {
                socket.close();
            } catch (IOException ignored) {
                // 關閉失敗不影響其他連線
            }
        }
    }

    private String describe() {
        UserInfo u = user;
        return clientId + (u == null ? "" : "（" + u.username() + "）") + " " + socket.getRemoteSocketAddress();
    }
}
