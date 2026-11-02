package com.exambattle.server.room;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.ProtocolException;
import com.exambattle.common.protocol.payload.LeftRoom;
import com.exambattle.common.protocol.payload.RoomCloseReason;
import com.exambattle.common.protocol.payload.RoomClosed;
import com.exambattle.common.protocol.payload.RoomLeaveReason;
import com.exambattle.common.protocol.payload.RoomPlayerJoined;
import com.exambattle.common.protocol.payload.RoomPlayerLeft;
import com.exambattle.common.protocol.game.Kicked;
import com.exambattle.server.Session;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 管理所有進行中的房間：建立、加入、離開、解散（房主離開即解散，見 DECISIONS.md D-012）。
 *
 * <p>房間只存在記憶體。所有操作在與 ClientRegistry 共用的鎖內完成。
 * 呼叫端須保證 session 已登入。
 */
public final class RoomManager {

    /** 房間成員變動的通知（給多人競賽使用），在鎖內呼叫。 */
    public interface Listener {
        /** 非房主的玩家離開、斷線或被移出。 */
        void playerLeft(Room room, Session player);

        /** 房間解散（房主離開或斷線）。 */
        void roomClosed(Room room);
    }

    private final Object lock;
    private Listener listener = new Listener() {
        @Override
        public void playerLeft(Room room, Session player) {
        }

        @Override
        public void roomClosed(Room room) {
        }
    };
    private final RoomCodeGenerator codes;
    private final int maxPlayers;
    private final Map<String, Room> rooms = new HashMap<>();
    private final Map<Long, Room> roomByUser = new HashMap<>();

    public RoomManager(Object lock, RoomCodeGenerator codes, int maxPlayers) {
        this.lock = lock;
        this.codes = codes;
        this.maxPlayers = maxPlayers;
    }

    /** 設定成員變動的通知對象；只在啟動時呼叫一次。 */
    public void setListener(Listener listener) {
        synchronized (lock) {
            this.listener = listener;
        }
    }

    /** 建立房間，建立者為房主；回覆 ROOM_STATE。 */
    public void create(Session session, String requestId) throws ProtocolException {
        synchronized (lock) {
            requireNotInRoom(session, requestId);
            Room room = new Room(codes.next(rooms::containsKey), session, maxPlayers);
            rooms.put(room.code(), room);
            roomByUser.put(userId(session), room);
            session.send(Message.of(MessageType.ROOM_STATE, requestId, room.state()));
        }
    }

    /** 以代碼加入房間；回覆 ROOM_STATE 並通知房內其他人。 */
    public void join(Session session, String rawCode, String requestId) throws ProtocolException {
        String code = RoomCodeGenerator.normalize(rawCode);
        synchronized (lock) {
            requireNotInRoom(session, requestId);
            Room room = rooms.get(code);
            if (room == null) {
                throw new ProtocolException(ErrorCode.ROOM_NOT_FOUND, "找不到房間 " + code, requestId);
            }
            if (room.playing()) {
                throw new ProtocolException(ErrorCode.GAME_IN_PROGRESS, "房間 " + code + " 正在競賽中，請等這一場結束", requestId);
            }
            if (room.isFull()) {
                throw new ProtocolException(ErrorCode.ROOM_FULL, "房間 " + code + " 已滿", requestId);
            }
            Message joined = Message.of(MessageType.ROOM_PLAYER_JOINED, null,
                    new RoomPlayerJoined(code, session.user()));
            room.members().forEach(s -> s.send(joined));
            room.add(session);
            roomByUser.put(userId(session), room);
            session.send(Message.of(MessageType.ROOM_STATE, requestId, room.state()));
        }
    }

    /** 主動離開房間；回覆 LEFT_ROOM。 */
    public void leave(Session session, String requestId) throws ProtocolException {
        synchronized (lock) {
            Room room = requireRoom(session, requestId);
            remove(session, room, false);
            session.send(Message.of(MessageType.LEFT_ROOM, requestId, new LeftRoom(room.code())));
        }
    }

    /**
     * 房主在競賽開始前把玩家移出房間：被移出的人收到 KICKED，其他人收到 ROOM_PLAYER_LEFT（KICKED），
     * 房主收到更新後的 ROOM_STATE。
     */
    public void kick(Session session, long userId, String requestId) throws ProtocolException {
        synchronized (lock) {
            Room room = requireOwnedRoom(session, requestId);
            if (room.playing()) {
                throw new ProtocolException(ErrorCode.GAME_IN_PROGRESS, "競賽進行中不能移出玩家", requestId);
            }
            Session target = room.member(userId);
            if (target == null) {
                throw new ProtocolException(ErrorCode.PLAYER_NOT_FOUND, "房間裡沒有這位玩家", requestId);
            }
            if (target == session) {
                throw new ProtocolException(ErrorCode.INVALID_PAYLOAD, "房主不能移出自己，請改用離開房間", requestId);
            }
            roomByUser.remove(userId);
            room.remove(target);
            target.send(Message.of(MessageType.KICKED, null, new Kicked(room.code())));
            Message left = Message.of(MessageType.ROOM_PLAYER_LEFT, null,
                    new RoomPlayerLeft(room.code(), target.user(), RoomLeaveReason.KICKED));
            room.members().stream().filter(s -> s != session).forEach(s -> s.send(left));
            listener.playerLeft(room, target);
            session.send(Message.of(MessageType.ROOM_STATE, requestId, room.state()));
        }
    }

    /** 自己當房主的房間；呼叫端須持有鎖。 */
    public Room requireOwnedRoom(Session session, String requestId) throws ProtocolException {
        Room room = requireRoom(session, requestId);
        if (!room.isOwner(session)) {
            throw new ProtocolException(ErrorCode.NOT_ROOM_OWNER, "只有房主可以這麼做", requestId);
        }
        return room;
    }

    /** 自己所在的房間；呼叫端須持有鎖。 */
    public Room requireRoomOf(Session session, String requestId) throws ProtocolException {
        return requireRoom(session, requestId);
    }

    /** 共用的鎖（與 ClientRegistry 相同）。 */
    public Object lock() {
        return lock;
    }

    /** 回覆目前所在房間的 ROOM_STATE。 */
    public void sendState(Session session, String requestId) throws ProtocolException {
        synchronized (lock) {
            Room room = requireRoom(session, requestId);
            session.send(Message.of(MessageType.ROOM_STATE, requestId, room.state()));
        }
    }

    /** 連線中斷時呼叫；未登入或不在房間則不做事。 */
    public void onDisconnect(Session session) {
        synchronized (lock) {
            if (session.user() == null) {
                return;
            }
            Room room = roomByUser.get(userId(session));
            if (room != null) {
                remove(session, room, true);
            }
        }
    }

    /** 使用者目前所在的房間。 */
    public Optional<Room> roomOf(Session session) {
        synchronized (lock) {
            return session.user() == null
                    ? Optional.empty()
                    : Optional.ofNullable(roomByUser.get(userId(session)));
        }
    }

    public int roomCount() {
        synchronized (lock) {
            return rooms.size();
        }
    }

    private void remove(Session session, Room room, boolean disconnected) {
        roomByUser.remove(userId(session));
        room.remove(session);
        if (room.isOwner(session)) {
            rooms.remove(room.code());
            RoomCloseReason reason = disconnected ? RoomCloseReason.OWNER_DISCONNECTED : RoomCloseReason.OWNER_LEFT;
            Message closed = Message.of(MessageType.ROOM_CLOSED, null, new RoomClosed(room.code(), reason));
            for (Session member : room.members()) {
                roomByUser.remove(userId(member));
                member.send(closed);
            }
            listener.roomClosed(room);
        } else {
            RoomLeaveReason reason = disconnected ? RoomLeaveReason.DISCONNECTED : RoomLeaveReason.LEFT;
            Message left = Message.of(MessageType.ROOM_PLAYER_LEFT, null,
                    new RoomPlayerLeft(room.code(), session.user(), reason));
            room.members().forEach(s -> s.send(left));
            listener.playerLeft(room, session);
        }
    }

    private void requireNotInRoom(Session session, String requestId) throws ProtocolException {
        Room current = roomByUser.get(userId(session));
        if (current != null) {
            throw new ProtocolException(ErrorCode.ALREADY_IN_ROOM, "你已在房間 " + current.code() + " 內", requestId);
        }
    }

    private Room requireRoom(Session session, String requestId) throws ProtocolException {
        Room room = roomByUser.get(userId(session));
        if (room == null) {
            throw new ProtocolException(ErrorCode.NOT_IN_ROOM, "你不在任何房間", requestId);
        }
        return room;
    }

    private static long userId(Session session) {
        return session.user().userId();
    }
}
