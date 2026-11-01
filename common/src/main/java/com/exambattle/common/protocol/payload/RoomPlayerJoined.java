package com.exambattle.common.protocol.payload;

/** ROOM_PLAYER_JOINED：有玩家加入同一房間。 */
public record RoomPlayerJoined(String roomCode, UserInfo player) {
}
