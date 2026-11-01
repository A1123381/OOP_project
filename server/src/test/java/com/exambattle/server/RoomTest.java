package com.exambattle.server;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.payload.JoinRoomRequest;
import com.exambattle.common.protocol.payload.LeftRoom;
import com.exambattle.common.protocol.payload.RoomCloseReason;
import com.exambattle.common.protocol.payload.RoomClosed;
import com.exambattle.common.protocol.payload.RoomLeaveReason;
import com.exambattle.common.protocol.payload.RoomPlayerJoined;
import com.exambattle.common.protocol.payload.RoomPlayerLeft;
import com.exambattle.common.protocol.payload.RoomState;
import com.exambattle.common.protocol.payload.UserInfo;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 建立、加入、離開、解散房間與顯示房間玩家。 */
class RoomTest extends ServerTestBase {

    @Test
    void createRoomMakesCreatorTheOwner() throws Exception {
        TestClient alice = loggedIn("alice");

        RoomState room = alice.createRoom();

        assertEquals(Protocol.ROOM_CODE_LENGTH, room.roomCode().length());
        assertTrue(room.roomCode().chars().allMatch(ch -> Protocol.ROOM_CODE_ALPHABET.indexOf(ch) >= 0));
        assertEquals(List.of("alice"), usernames(room));
        assertEquals(room.players().get(0).userId(), room.ownerUserId());
        assertEquals(50, room.maxPlayers());
        assertEquals(1, server.roomCount());
    }

    @Test
    void joinRoomByCodeIgnoringCaseAndShowsPlayers() throws Exception {
        TestClient alice = loggedIn("alice");
        TestClient bob = loggedIn("bob");
        String code = alice.createRoom().roomCode();

        RoomState state = bob.joinRoom("  " + code.toLowerCase() + " ");

        assertEquals(code, state.roomCode());
        assertEquals(List.of("alice", "bob"), usernames(state));
        RoomPlayerJoined joined = alice.nextOfType(MessageType.ROOM_PLAYER_JOINED).payloadAs(RoomPlayerJoined.class);
        assertEquals("bob", joined.player().username());

        RoomState refreshed = alice.request(MessageType.GET_ROOM, null, MessageType.ROOM_STATE)
                .payloadAs(RoomState.class);
        assertEquals(List.of("alice", "bob"), usernames(refreshed));
    }

    @Test
    void joinErrors() throws Exception {
        restartServer(testConfig().withMaxRoomPlayers(2));
        TestClient alice = loggedIn("alice");
        TestClient bob = loggedIn("bob");
        TestClient carol = loggedIn("carol");
        String code = alice.createRoom().roomCode();
        bob.joinRoom(code);

        carol.expectError(MessageType.JOIN_ROOM, new JoinRoomRequest("ZZZZZZ"), ErrorCode.ROOM_NOT_FOUND);
        carol.expectError(MessageType.JOIN_ROOM, new JoinRoomRequest(code), ErrorCode.ROOM_FULL);
        carol.expectError(MessageType.JOIN_ROOM, new JoinRoomRequest(" "), ErrorCode.INVALID_PAYLOAD);
        bob.expectError(MessageType.JOIN_ROOM, new JoinRoomRequest(code), ErrorCode.ALREADY_IN_ROOM);
        bob.expectError(MessageType.CREATE_ROOM, null, ErrorCode.ALREADY_IN_ROOM);
    }

    @Test
    void leaveAndGetRoomRequireBeingInRoom() throws Exception {
        TestClient alice = loggedIn("alice");

        alice.expectError(MessageType.LEAVE_ROOM, null, ErrorCode.NOT_IN_ROOM);
        alice.expectError(MessageType.GET_ROOM, null, ErrorCode.NOT_IN_ROOM);
    }

    @Test
    void playerLeavingNotifiesOthers() throws Exception {
        TestClient alice = loggedIn("alice");
        TestClient bob = loggedIn("bob");
        String code = alice.createRoom().roomCode();
        UserInfo bobInfo = bob.joinRoom(code).players().get(1);

        LeftRoom left = bob.request(MessageType.LEAVE_ROOM, null, MessageType.LEFT_ROOM).payloadAs(LeftRoom.class);

        assertEquals(code, left.roomCode());
        RoomPlayerLeft notice = alice.nextOfType(MessageType.ROOM_PLAYER_LEFT).payloadAs(RoomPlayerLeft.class);
        assertEquals(new RoomPlayerLeft(code, bobInfo, RoomLeaveReason.LEFT), notice);
        assertEquals(List.of("alice"), usernames(
                alice.request(MessageType.GET_ROOM, null, MessageType.ROOM_STATE).payloadAs(RoomState.class)));
        assertEquals(code, bob.joinRoom(code).roomCode(), "離開後可以重新加入");
    }

    @Test
    void ownerLeavingClosesRoom() throws Exception {
        TestClient alice = loggedIn("alice");
        TestClient bob = loggedIn("bob");
        TestClient carol = loggedIn("carol");
        String code = alice.createRoom().roomCode();
        bob.joinRoom(code);
        carol.joinRoom(code);

        alice.request(MessageType.LEAVE_ROOM, null, MessageType.LEFT_ROOM);

        for (TestClient member : List.of(bob, carol)) {
            RoomClosed closed = member.nextOfType(MessageType.ROOM_CLOSED).payloadAs(RoomClosed.class);
            assertEquals(new RoomClosed(code, RoomCloseReason.OWNER_LEFT), closed);
            member.expectError(MessageType.GET_ROOM, null, ErrorCode.NOT_IN_ROOM);
        }
        assertEquals(0, server.roomCount());
        carol.expectError(MessageType.JOIN_ROOM, new JoinRoomRequest(code), ErrorCode.ROOM_NOT_FOUND);
        bob.createRoom();
    }

    @Test
    void ownerDisconnectClosesRoom() throws Exception {
        TestClient alice = loggedIn("alice");
        TestClient bob = loggedIn("bob");
        String code = alice.createRoom().roomCode();
        bob.joinRoom(code);

        alice.close();

        RoomClosed closed = bob.nextOfType(MessageType.ROOM_CLOSED).payloadAs(RoomClosed.class);
        assertEquals(RoomCloseReason.OWNER_DISCONNECTED, closed.reason());
        bob.nextOfType(MessageType.CLIENT_LEFT);
        assertEquals(0, server.roomCount());
    }

    @Test
    void memberDisconnectIsReportedToRoom() throws Exception {
        TestClient alice = loggedIn("alice");
        TestClient bob = loggedIn("bob");
        String code = alice.createRoom().roomCode();
        bob.joinRoom(code);

        bob.close();

        RoomPlayerLeft left = alice.nextOfType(MessageType.ROOM_PLAYER_LEFT).payloadAs(RoomPlayerLeft.class);
        assertEquals(RoomLeaveReason.DISCONNECTED, left.reason());
        assertEquals(1, server.roomCount());
    }

    @Test
    void roomCodesAreUnique() throws Exception {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            codes.add(loggedIn("user" + i).createRoom().roomCode());
        }
        assertEquals(20, codes.size());
    }

    private static List<String> usernames(RoomState state) {
        return state.players().stream().map(UserInfo::username).toList();
    }
}
