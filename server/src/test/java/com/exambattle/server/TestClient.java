package com.exambattle.server;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageCodec;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.payload.ErrorPayload;
import com.exambattle.common.protocol.payload.HelloPayload;
import com.exambattle.common.protocol.payload.JoinRoomRequest;
import com.exambattle.common.protocol.payload.LoginOk;
import com.exambattle.common.protocol.payload.LoginRequest;
import com.exambattle.common.protocol.payload.RegisterRequest;
import com.exambattle.common.protocol.payload.RoomState;
import com.exambattle.common.protocol.payload.UserInfo;
import com.exambattle.common.protocol.payload.WelcomePayload;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/** 測試用的同步客戶端：直接收送原始行，方便驗證伺服器行為。 */
public final class TestClient implements AutoCloseable {

    public static final String PASSWORD = "password123";

    private static final int READ_TIMEOUT_MILLIS = 5_000;

    private final Socket socket;
    private final BufferedReader in;
    private final Writer out;
    private int nextRequest = 1;

    public TestClient(int port) throws IOException {
        socket = new Socket("127.0.0.1", port);
        socket.setSoTimeout(READ_TIMEOUT_MILLIS);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    /** 送出 HELLO 並等待 WELCOME。 */
    public WelcomePayload hello() throws Exception {
        return request(MessageType.HELLO, new HelloPayload(1), MessageType.WELCOME).payloadAs(WelcomePayload.class);
    }

    public UserInfo register(String username, String displayName) throws Exception {
        return request(MessageType.REGISTER, new RegisterRequest(username, PASSWORD, displayName),
                MessageType.REGISTER_OK).payloadAs(UserInfo.class);
    }

    public LoginOk login(String username) throws Exception {
        return request(MessageType.LOGIN, new LoginRequest(username, PASSWORD), MessageType.LOGIN_OK)
                .payloadAs(LoginOk.class);
    }

    /** HELLO → REGISTER → LOGIN，回傳登入的使用者。 */
    public UserInfo signUpAndLogin(String username) throws Exception {
        hello();
        register(username, username.toUpperCase());
        return login(username).user();
    }

    public RoomState createRoom() throws Exception {
        return request(MessageType.CREATE_ROOM, null, MessageType.ROOM_STATE).payloadAs(RoomState.class);
    }

    public RoomState joinRoom(String code) throws Exception {
        return request(MessageType.JOIN_ROOM, new JoinRoomRequest(code), MessageType.ROOM_STATE)
                .payloadAs(RoomState.class);
    }

    /** 送出請求，略過推播訊息，直到收到帶相同 requestId 的回應；回應類型不符則測試失敗。 */
    public Message request(MessageType type, Object payload, MessageType expected) throws Exception {
        String requestId = send(type, payload);
        Message reply = replyTo(requestId);
        if (!reply.is(expected)) {
            throw new AssertionError("預期 " + expected + "，收到 " + MessageCodec.encode(reply));
        }
        return reply;
    }

    /** 送出請求並預期收到指定錯誤碼。 */
    public ErrorPayload expectError(MessageType type, Object payload, ErrorCode code) throws Exception {
        String requestId = send(type, payload);
        Message reply = replyTo(requestId);
        if (!reply.is(MessageType.ERROR)) {
            throw new AssertionError("預期 ERROR " + code + "，收到 " + MessageCodec.encode(reply));
        }
        ErrorPayload error = reply.payloadAs(ErrorPayload.class);
        if (error.code() != code) {
            throw new AssertionError("預期 " + code + "，收到 " + error.code() + "：" + error.message());
        }
        return error;
    }

    /** 送出訊息並回傳使用的 requestId。 */
    public String send(MessageType type, Object payload) throws IOException {
        String requestId = "t-" + nextRequest++;
        sendRaw(MessageCodec.encode(Message.of(type, requestId, payload)));
        return requestId;
    }

    public void sendRaw(String line) throws IOException {
        out.write(line);
        out.write('\n');
        out.flush();
    }

    /** 讀取下一則訊息；逾時或連線關閉時測試失敗。 */
    public Message next() throws Exception {
        String line = in.readLine();
        if (line == null) {
            throw new AssertionError("連線已被關閉");
        }
        return MessageCodec.decode(line);
    }

    /** 略過其他類型，直到收到指定類型的訊息。 */
    public Message nextOfType(MessageType type) throws Exception {
        while (true) {
            Message m = next();
            if (m.is(type)) {
                return m;
            }
        }
    }

    private Message replyTo(String requestId) throws Exception {
        while (true) {
            Message m = next();
            if (requestId.equals(m.requestId())) {
                return m;
            }
        }
    }

    /** 確認在 waitMillis 內沒有收到指定類型的訊息（其他類型會被略過）。 */
    public void assertNoMessageOfType(MessageType type, int waitMillis) throws Exception {
        socket.setSoTimeout(waitMillis);
        try {
            while (true) {
                Message m = next();
                if (m.is(type)) {
                    throw new AssertionError("不應收到 " + MessageCodec.encode(m));
                }
            }
        } catch (SocketTimeoutException expected) {
            // 期間內沒有收到
        } finally {
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
        }
    }

    /** 連線是否已被伺服器關閉（讀到 EOF 或 reset）。最多略過 limit 則訊息。 */
    public boolean isClosedByServer(int limit) throws IOException {
        try {
            for (int i = 0; i <= limit; i++) {
                if (in.readLine() == null) {
                    return true;
                }
            }
            return false;
        } catch (SocketTimeoutException e) {
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
