package com.exambattle.client.text;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.payload.ChatBroadcast;
import com.exambattle.common.protocol.payload.ClientLeftPayload;
import com.exambattle.common.protocol.payload.ErrorPayload;
import com.exambattle.common.protocol.payload.LeaveReason;
import com.exambattle.common.protocol.payload.LoginOk;
import com.exambattle.common.protocol.payload.RoomCloseReason;
import com.exambattle.common.protocol.payload.RoomClosed;
import com.exambattle.common.protocol.payload.RoomLeaveReason;
import com.exambattle.common.protocol.payload.RoomPlayerLeft;
import com.exambattle.common.protocol.payload.RoomState;
import com.exambattle.common.protocol.payload.UserInfo;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageFormatterTest {

    private static final UserInfo ALICE = new UserInfo(1, "alice", "Alice");
    private static final UserInfo BOB = new UserInfo(2, "bob", "Bob");

    private final MessageFormatter formatter = new MessageFormatter(ZoneId.of("Asia/Taipei"));

    private String format(MessageType type, Object payload, UserInfo me) throws Exception {
        return formatter.format(Message.of(type, null, payload), me).orElseThrow();
    }

    @Test
    void loginListsOnlineUsers() throws Exception {
        String text = format(MessageType.LOGIN_OK, new LoginOk(ALICE, List.of(BOB)), null);

        assertTrue(text.startsWith("* 登入成功，歡迎 Alice！目前在線：Bob(bob)"), text);
    }

    @Test
    void roomStateMarksOwnerAndMe() throws Exception {
        String text = format(MessageType.ROOM_STATE, new RoomState("K7M2XQ", 2, 50, List.of(BOB, ALICE)), ALICE);

        assertEquals("""
                * 房間 K7M2XQ（2/50 人）
                    Bob(bob)【房主】
                    Alice(alice)（你）""", text);
    }

    @Test
    void roomEvents() throws Exception {
        assertEquals("* Bob(bob) 離開了房間",
                format(MessageType.ROOM_PLAYER_LEFT, new RoomPlayerLeft("K7M2XQ", BOB, RoomLeaveReason.LEFT), ALICE));
        assertEquals("* 房間 K7M2XQ 已解散（房主斷線），你已回到大廳",
                format(MessageType.ROOM_CLOSED, new RoomClosed("K7M2XQ", RoomCloseReason.OWNER_DISCONNECTED), ALICE));
    }

    @Test
    void chatShowsLocalTimeAndMarksOwnMessages() throws Exception {
        ChatBroadcast chat = new ChatBroadcast(1, "Alice", "hi", "2026-11-01T04:05:06.789Z", "K7M2XQ");

        assertEquals("[12:05:06] Alice（你）: hi", format(MessageType.CHAT, chat, ALICE));
        assertEquals("[12:05:06] Alice: hi", format(MessageType.CHAT, chat, BOB));
    }

    @Test
    void presenceAndErrors() throws Exception {
        assertEquals("* Bob(bob) 上線了", format(MessageType.CLIENT_JOINED, BOB, ALICE));
        assertEquals("* Bob(bob) 逾時離線",
                format(MessageType.CLIENT_LEFT, new ClientLeftPayload(BOB, LeaveReason.TIMEOUT), ALICE));
        assertEquals("! 錯誤 [ROOM_NOT_FOUND] 找不到房間 ZZZZZZ", format(MessageType.ERROR,
                new ErrorPayload(ErrorCode.ROOM_NOT_FOUND, "找不到房間 ZZZZZZ"), ALICE));
    }

    @Test
    void pongIsNotDisplayed() throws Exception {
        assertEquals(Optional.empty(), formatter.format(Message.empty(MessageType.PONG, "c-1"), null));
    }
}
