package com.exambattle.common.protocol.payload;

import java.util.List;

/** ROOM_STATE：房間目前的狀態；players 依加入順序排列，第一位是房主。 */
public record RoomState(String roomCode, long ownerUserId, int maxPlayers, List<UserInfo> players) {
}
