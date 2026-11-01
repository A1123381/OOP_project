package com.exambattle.server;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.payload.ChatBroadcast;
import com.exambattle.common.protocol.payload.ChatRequest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** 大廳與房間的聊天範圍互不外流（DECISIONS.md D-013）。 */
class ChatScopeTest extends ServerTestBase {

    @Test
    void lobbyChatReachesOnlyLobby() throws Exception {
        TestClient alice = loggedIn("alice");
        TestClient bob = loggedIn("bob");
        TestClient roomOwner = loggedIn("carol");
        roomOwner.createRoom();

        String requestId = alice.send(MessageType.CHAT, new ChatRequest("大廳好"));

        Message toSender = alice.nextOfType(MessageType.CHAT);
        assertEquals(requestId, toSender.requestId());
        ChatBroadcast chat = bob.nextOfType(MessageType.CHAT).payloadAs(ChatBroadcast.class);
        assertEquals("大廳好", chat.text());
        assertEquals("ALICE", chat.fromName());
        assertNull(chat.roomCode());
        assertNotNull(chat.serverTime());
        roomOwner.assertNoMessageOfType(MessageType.CHAT, 300);
    }

    @Test
    void roomChatReachesOnlySameRoom() throws Exception {
        TestClient alice = loggedIn("alice");
        TestClient bob = loggedIn("bob");
        TestClient lobby = loggedIn("lobby");
        TestClient other = loggedIn("other");
        String code = alice.createRoom().roomCode();
        bob.joinRoom(code);
        other.createRoom();

        bob.send(MessageType.CHAT, new ChatRequest("房間內"));

        ChatBroadcast chat = alice.nextOfType(MessageType.CHAT).payloadAs(ChatBroadcast.class);
        assertEquals("房間內", chat.text());
        assertEquals(code, chat.roomCode());
        assertNotNull(bob.nextOfType(MessageType.CHAT).requestId(), "發送者收到的那則帶回 requestId");
        lobby.assertNoMessageOfType(MessageType.CHAT, 300);
        other.assertNoMessageOfType(MessageType.CHAT, 100);
    }

    @Test
    void clientCannotSetServerTimeOrRoom() throws Exception {
        TestClient alice = loggedIn("alice");

        alice.send(MessageType.CHAT, Map.of("text", "hi", "serverTime", "1999-01-01T00:00:00Z", "roomCode", "FAKE22"));

        ChatBroadcast chat = alice.nextOfType(MessageType.CHAT).payloadAs(ChatBroadcast.class);
        assertFalse(chat.serverTime().startsWith("1999"), "serverTime 必須由伺服器產生");
        assertNull(chat.roomCode());
    }

    @Test
    void invalidChatTextIsRejected() throws Exception {
        TestClient alice = loggedIn("alice");

        alice.expectError(MessageType.CHAT, new ChatRequest("   "), ErrorCode.INVALID_PAYLOAD);
        alice.expectError(MessageType.CHAT, new ChatRequest("x".repeat(1001)), ErrorCode.INVALID_PAYLOAD);
        alice.expectError(MessageType.CHAT, Map.of(), ErrorCode.INVALID_PAYLOAD);
    }
}
