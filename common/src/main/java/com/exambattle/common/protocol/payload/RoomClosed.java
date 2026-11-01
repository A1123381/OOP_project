package com.exambattle.common.protocol.payload;

/** ROOM_CLOSED：房間解散，收到者回到大廳。 */
public record RoomClosed(String roomCode, RoomCloseReason reason) {
}
