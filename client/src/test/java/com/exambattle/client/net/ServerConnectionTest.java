package com.exambattle.client.net;

import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageCodec;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.payload.ChatRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import com.exambattle.common.protocol.ErrorCode;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 以假的伺服器 socket 驗證客戶端網路層。 */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class ServerConnectionTest {

    private final BlockingQueue<Message> received = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> disconnects = new LinkedBlockingQueue<>();
    private final ServerConnection.Listener listener = new ServerConnection.Listener() {
        @Override
        public void onMessage(Message message) {
            received.add(message);
        }

        @Override
        public void onDisconnected(String reason) {
            disconnects.add(reason);
        }
    };

    private ServerSocket fakeServer;
    private ServerConnection connection;
    private Socket serverSide;
    private BufferedReader serverIn;
    private PrintWriter serverOut;

    @BeforeEach
    void connect() throws Exception {
        fakeServer = new ServerSocket(0);
        connection = ServerConnection.connect("127.0.0.1", fakeServer.getLocalPort(), listener);
        serverSide = fakeServer.accept();
        serverIn = new BufferedReader(new InputStreamReader(serverSide.getInputStream(), StandardCharsets.UTF_8));
        serverOut = new PrintWriter(serverSide.getOutputStream(), true, StandardCharsets.UTF_8);
    }

    @AfterEach
    void close() throws Exception {
        connection.close();
        serverSide.close();
        fakeServer.close();
    }

    @Test
    void sendWritesOneJsonLineWithIncreasingRequestIds() throws Exception {
        String first = connection.send(MessageType.CHAT, new ChatRequest("考試"));
        String second = connection.send(MessageType.PING, null);

        Message chat = MessageCodec.decode(serverIn.readLine());
        assertEquals("CHAT", chat.type());
        assertEquals(first, chat.requestId());
        assertEquals("考試", chat.payloadAs(ChatRequest.class).text());
        assertEquals(second, MessageCodec.decode(serverIn.readLine()).requestId());
        assertEquals("c-1", first);
        assertEquals("c-2", second);
    }

    @Test
    void serverPingIsAnsweredAutomaticallyAndNotForwarded() throws Exception {
        serverOut.println("{\"type\":\"PING\",\"requestId\":\"s-1\",\"payload\":{}}");

        Message pong = MessageCodec.decode(serverIn.readLine());
        assertEquals("PONG", pong.type());
        assertEquals("s-1", pong.requestId());

        serverOut.println("{\"type\":\"CHAT\",\"requestId\":null,\"payload\":{\"text\":\"x\"}}");
        assertEquals("CHAT", received.poll(5, TimeUnit.SECONDS).type());
    }

    @Test
    void serverClosingTriggersOnDisconnected() throws Exception {
        serverSide.close();

        assertNotNull(disconnects.poll(5, TimeUnit.SECONDS));
    }

    @Test
    void requestCompletesWithMatchingReplyAfterListener() throws Exception {
        CompletableFuture<Message> future = connection.request(MessageType.GET_ROOM, null);
        Message sent = MessageCodec.decode(serverIn.readLine());

        serverOut.println("{\"type\":\"CHAT\",\"requestId\":null,\"payload\":{\"text\":\"推播\"}}");
        serverOut.println("{\"type\":\"ROOM_STATE\",\"requestId\":\"" + sent.requestId() + "\",\"payload\":{}}");

        Message reply = future.get(5, TimeUnit.SECONDS);
        assertEquals("ROOM_STATE", reply.type());
        assertEquals("CHAT", received.poll(5, TimeUnit.SECONDS).type(), "推播訊息照舊交給 Listener");
        assertEquals("ROOM_STATE", received.poll(5, TimeUnit.SECONDS).type(), "回應也會交給 Listener");
    }

    @Test
    void errorReplyFailsTheFuture() throws Exception {
        CompletableFuture<Message> future = connection.request(MessageType.JOIN_ROOM, null);
        Message sent = MessageCodec.decode(serverIn.readLine());

        serverOut.println("{\"type\":\"ERROR\",\"requestId\":\"" + sent.requestId()
                + "\",\"payload\":{\"code\":\"ROOM_NOT_FOUND\",\"message\":\"找不到房間\"}}");

        ExecutionException e = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
        ServerErrorException error = assertInstanceOf(ServerErrorException.class, e.getCause());
        assertEquals(ErrorCode.ROOM_NOT_FOUND, error.code());
        assertEquals("找不到房間", error.getMessage());
    }

    @Test
    void pendingRequestsFailWhenServerDisconnects() throws Exception {
        CompletableFuture<Message> future = connection.request(MessageType.GET_ROOM, null);
        serverIn.readLine();

        serverSide.close();

        ExecutionException e = assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
        assertInstanceOf(java.io.IOException.class, e.getCause());
    }
}
