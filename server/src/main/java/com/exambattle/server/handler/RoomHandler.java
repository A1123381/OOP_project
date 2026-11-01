package com.exambattle.server.handler;

import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.ProtocolException;
import com.exambattle.common.protocol.payload.JoinRoomRequest;
import com.exambattle.server.Session;
import com.exambattle.server.room.RoomManager;

/** 處理 CREATE_ROOM、JOIN_ROOM、LEAVE_ROOM、GET_ROOM。呼叫端須保證已登入。 */
public final class RoomHandler {

    private final RoomManager rooms;

    public RoomHandler(RoomManager rooms) {
        this.rooms = rooms;
    }

    public void handle(Session session, MessageType type, Message message) throws ProtocolException {
        String requestId = message.requestId();
        switch (type) {
            case CREATE_ROOM -> rooms.create(session, requestId);
            case JOIN_ROOM -> {
                String code = message.payloadAs(JoinRoomRequest.class).roomCode();
                if (code == null || code.isBlank()) {
                    throw PayloadValidator.invalid("缺少 roomCode", requestId);
                }
                rooms.join(session, code, requestId);
            }
            case LEAVE_ROOM -> rooms.leave(session, requestId);
            case GET_ROOM -> rooms.sendState(session, requestId);
            default -> throw new IllegalArgumentException("不是房間訊息: " + type);
        }
    }
}
