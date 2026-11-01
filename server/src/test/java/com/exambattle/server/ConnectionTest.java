package com.exambattle.server;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.payload.ByePayload;
import com.exambattle.common.protocol.payload.ChatRequest;
import com.exambattle.common.protocol.payload.ClientLeftPayload;
import com.exambattle.common.protocol.payload.ErrorPayload;
import com.exambattle.common.protocol.payload.HelloPayload;
import com.exambattle.common.protocol.payload.LeaveReason;
import com.exambattle.common.protocol.payload.WelcomePayload;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 連線層：握手、心跳、錯誤處理、斷線與關閉。 */
class ConnectionTest extends ServerTestBase {

    @Test
    void helloIsAnsweredWithWelcome() throws Exception {
        WelcomePayload welcome = connect().hello();

        assertNotNull(welcome.clientId());
        assertEquals(1, welcome.protocolVersion());
    }

    @Test
    void unsupportedProtocolVersionIsRejected() throws Exception {
        connect().expectError(MessageType.HELLO, new HelloPayload(99), ErrorCode.INVALID_PAYLOAD);
    }

    @Test
    void secondHelloIsRejected() throws Exception {
        TestClient c = connect();
        c.hello();

        c.expectError(MessageType.HELLO, new HelloPayload(1), ErrorCode.ALREADY_WELCOMED);
    }

    @Test
    void messagesBeforeHelloAreRejectedButPingWorks() throws Exception {
        TestClient c = connect();

        c.expectError(MessageType.CHAT, new ChatRequest("hi"), ErrorCode.NOT_HELLO);
        c.expectError(MessageType.LOGIN, null, ErrorCode.NOT_HELLO);
        c.request(MessageType.PING, null, MessageType.PONG);
    }

    @Test
    void unknownAndServerOnlyTypesAreRejectedWithoutClosing() throws Exception {
        TestClient c = connect();
        c.hello();

        c.sendRaw("{\"type\":\"DANCE\",\"requestId\":\"r1\",\"payload\":{}}");
        assertError(c.next(), ErrorCode.UNKNOWN_TYPE, "r1");
        c.sendRaw("{\"type\":\"ROOM_STATE\",\"requestId\":\"r2\",\"payload\":{}}");
        assertError(c.next(), ErrorCode.UNKNOWN_TYPE, "r2");

        c.request(MessageType.PING, null, MessageType.PONG);
    }

    @Test
    void badJsonIsRejectedWithoutClosing() throws Exception {
        TestClient c = connect();

        c.sendRaw("this is not json");
        assertError(c.next(), ErrorCode.BAD_JSON, null);
        c.sendRaw("{\"type\":\"CHAT\",\"requestId\":\"r1\"}");
        assertError(c.next(), ErrorCode.BAD_JSON, "r1");

        c.hello();
    }

    @Test
    void byeClosesConnectionAndNotifiesOthers() throws Exception {
        TestClient alice = loggedIn("alice");
        TestClient bob = loggedIn("bob");
        alice.nextOfType(MessageType.CLIENT_JOINED);

        bob.send(MessageType.BYE, null);

        assertTrue(bob.isClosedByServer(0));
        ClientLeftPayload left = alice.nextOfType(MessageType.CLIENT_LEFT).payloadAs(ClientLeftPayload.class);
        assertEquals("bob", left.user().username());
        assertEquals(LeaveReason.BYE, left.reason());
        assertEquals(1, server.onlineCount());
    }

    @Test
    void abruptDisconnectIsReported() throws Exception {
        TestClient alice = loggedIn("alice");
        TestClient bob = loggedIn("bob");
        alice.nextOfType(MessageType.CLIENT_JOINED);

        bob.close();

        ClientLeftPayload left = alice.nextOfType(MessageType.CLIENT_LEFT).payloadAs(ClientLeftPayload.class);
        assertEquals(LeaveReason.DISCONNECTED, left.reason());
    }

    @Test
    void anonymousDisconnectIsNotBroadcast() throws Exception {
        TestClient alice = loggedIn("alice");
        TestClient anonymous = connect();
        anonymous.hello();

        anonymous.close();

        alice.assertNoMessageOfType(MessageType.CLIENT_LEFT, 300);
    }

    @Test
    void idleClientTimesOut() throws Exception {
        restartServer(testConfig().withIdleTimeout(Duration.ofMillis(500)));
        TestClient idle = loggedIn("idle");
        TestClient alice = loggedIn("alice");

        // Alice 持續 PING 保持連線，Idle 不送任何訊息
        Message left = null;
        for (int i = 0; i < 20 && left == null; i++) {
            alice.send(MessageType.PING, null);
            Message m = alice.next();
            if (m.is(MessageType.CLIENT_LEFT)) {
                left = m;
            }
            Thread.sleep(100);
        }
        assertNotNull(left, "Idle 應該因逾時離線");
        assertEquals(LeaveReason.TIMEOUT, left.payloadAs(ClientLeftPayload.class).reason());
        assertTrue(idle.isClosedByServer(1), "Idle 收到的 CLIENT_JOINED 之後應該就是 EOF");
    }

    @Test
    void tooLongLineIsRejectedAndConnectionClosed() throws Exception {
        TestClient c = connect();

        c.sendRaw("x".repeat(Protocol.MAX_LINE_CHARS + 10));

        assertError(c.next(), ErrorCode.LINE_TOO_LONG, null);
        assertTrue(c.isClosedByServer(0));
    }

    @Test
    void shutdownSendsByeToEveryone() throws Exception {
        TestClient alice = loggedIn("alice");
        TestClient anonymous = connect();
        anonymous.hello();

        server.close();

        assertEquals(ByePayload.SERVER_SHUTDOWN,
                alice.nextOfType(MessageType.BYE).payloadAs(ByePayload.class).reason());
        assertEquals("BYE", anonymous.next().type());
        assertTrue(alice.isClosedByServer(0));
    }

    private static void assertError(Message m, ErrorCode code, String requestId) throws Exception {
        assertEquals("ERROR", m.type());
        if (requestId == null) {
            assertNull(m.requestId());
        } else {
            assertEquals(requestId, m.requestId());
        }
        assertEquals(code, m.payloadAs(ErrorPayload.class).code());
    }
}
