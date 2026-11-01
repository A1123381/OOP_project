package com.exambattle.client.gui;

import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.payload.ChatBroadcast;
import com.exambattle.common.protocol.payload.ClientLeftPayload;
import com.exambattle.common.protocol.payload.LeaveReason;
import com.exambattle.common.protocol.payload.LoginOk;
import com.exambattle.common.protocol.payload.RoomCloseReason;
import com.exambattle.common.protocol.payload.RoomClosed;
import com.exambattle.common.protocol.payload.RoomLeaveReason;
import com.exambattle.common.protocol.payload.RoomPlayerJoined;
import com.exambattle.common.protocol.payload.RoomPlayerLeft;
import com.exambattle.common.protocol.payload.RoomState;
import com.exambattle.common.protocol.payload.UserInfo;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ClientState 只依伺服器訊息更新；不需啟動 JavaFX。 */
class ClientStateTest {

    private static final UserInfo ALICE = new UserInfo(1, "alice", "Alice");
    private static final UserInfo BOB = new UserInfo(2, "bob", "Bob");

    private final ClientState state = new ClientState(ZoneId.of("Asia/Taipei"));

    private void push(MessageType type, Object payload) {
        state.apply(Message.of(type, null, payload));
    }

    @Test
    void loginAndPresenceUpdateOnlineList() {
        push(MessageType.LOGIN_OK, new LoginOk(ALICE, List.of()));
        push(MessageType.CLIENT_JOINED, BOB);

        assertEquals(ALICE, state.me());
        assertEquals(List.of(BOB), state.online());

        push(MessageType.CLIENT_LEFT, new ClientLeftPayload(BOB, LeaveReason.BYE));
        assertTrue(state.online().isEmpty());
        assertTrue(state.lobbyChat().get(state.lobbyChat().size() - 1).contains("Bob"));
    }

    @Test
    void roomLifecycleAndChatScope() {
        push(MessageType.LOGIN_OK, new LoginOk(ALICE, List.of(BOB)));
        push(MessageType.ROOM_STATE, new RoomState("K7M2XQ", 1, 50, List.of(ALICE)));
        push(MessageType.ROOM_PLAYER_JOINED, new RoomPlayerJoined("K7M2XQ", BOB));

        assertEquals(List.of(ALICE, BOB), state.roomProperty().get().players());

        push(MessageType.CHAT, new ChatBroadcast(2, "Bob", "房間內", "2026-11-01T04:00:00Z", "K7M2XQ"));
        push(MessageType.CHAT, new ChatBroadcast(3, "Carol", "大廳", "2026-11-01T04:00:01Z", null));
        assertTrue(state.roomChat().stream().anyMatch(l -> l.endsWith("Bob: 房間內")));
        assertTrue(state.lobbyChat().stream().anyMatch(l -> l.endsWith("Carol: 大廳")));
        assertTrue(state.lobbyChat().stream().noneMatch(l -> l.contains("房間內")));

        push(MessageType.ROOM_PLAYER_LEFT, new RoomPlayerLeft("K7M2XQ", BOB, RoomLeaveReason.LEFT));
        assertEquals(List.of(ALICE), state.roomProperty().get().players());

        push(MessageType.ROOM_CLOSED, new RoomClosed("K7M2XQ", RoomCloseReason.OWNER_LEFT));
        assertNull(state.roomProperty().get());
    }

    @Test
    void chatHistoryIsBounded() {
        push(MessageType.LOGIN_OK, new LoginOk(ALICE, List.of()));
        for (int i = 0; i < ClientState.MAX_CHAT_LINES + 50; i++) {
            push(MessageType.CHAT, new ChatBroadcast(2, "Bob", "m" + i, "2026-11-01T04:00:00Z", null));
        }
        assertEquals(ClientState.MAX_CHAT_LINES, state.lobbyChat().size());
        assertTrue(state.lobbyChat().get(state.lobbyChat().size() - 1).endsWith("m" + (ClientState.MAX_CHAT_LINES + 49)));
    }
}
