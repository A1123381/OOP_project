package com.exambattle.common.protocol.payload;

/** ROOM_PLAYER_LEFT：有玩家離開同一房間。 */
public record RoomPlayerLeft(String roomCode, UserInfo player, RoomLeaveReason reason) {
}
