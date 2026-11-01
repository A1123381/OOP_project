package com.exambattle.server.handler;

import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.ProtocolException;
import com.exambattle.common.protocol.payload.ChatBroadcast;
import com.exambattle.common.protocol.payload.ChatRequest;
import com.exambattle.common.protocol.payload.UserInfo;
import com.exambattle.server.ClientRegistry;
import com.exambattle.server.Session;
import com.exambattle.server.room.Room;
import com.exambattle.server.room.RoomManager;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 處理 CHAT：在房間內只送給同房玩家，在大廳只送給大廳的人（見 DECISIONS.md D-013）。
 * 判斷所在位置與廣播在同一把鎖內，避免剛好進出房間時訊息送錯範圍。
 */
public final class ChatHandler {

    private final Object lock;
    private final ClientRegistry registry;
    private final RoomManager rooms;
    private final Clock clock;

    public ChatHandler(Object lock, ClientRegistry registry, RoomManager rooms, Clock clock) {
        this.lock = lock;
        this.registry = registry;
        this.rooms = rooms;
        this.clock = clock;
    }

    public void handle(Session from, Message message) throws ProtocolException {
        String text = PayloadValidator.chatText(message.payloadAs(ChatRequest.class), message.requestId());
        UserInfo user = from.user();
        synchronized (lock) {
            Optional<Room> room = rooms.roomOf(from);
            List<Session> recipients = room.map(Room::members)
                    .orElseGet(() -> registry.sessions().stream()
                            .filter(s -> rooms.roomOf(s).isEmpty())
                            .toList());
            String serverTime = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS).toString();
            ChatBroadcast chat = new ChatBroadcast(user.userId(), user.displayName(), text, serverTime,
                    room.map(Room::code).orElse(null));
            Message toSender = Message.of(MessageType.CHAT, message.requestId(), chat);
            Message toOthers = Message.of(MessageType.CHAT, null, chat);
            recipients.forEach(s -> s.send(s == from ? toSender : toOthers));
        }
    }
}
