package com.exambattle.common.protocol.payload;

/** JOIN_ROOM：以房間代碼加入（不分大小寫）。 */
public record JoinRoomRequest(String roomCode) {
}
