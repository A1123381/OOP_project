package com.exambattle.common.protocol.payload;

import java.util.List;

/**
 * ROOM_STATE：房間目前的狀態；players 依加入順序排列，第一位是房主。
 * playing 表示競賽進行中（第四階段）：這時不能加入或移出玩家。
 */
public record RoomState(String roomCode, long ownerUserId, int maxPlayers, List<UserInfo> players, boolean playing) {

    /** 等待中的房間。 */
    public RoomState(String roomCode, long ownerUserId, int maxPlayers, List<UserInfo> players) {
        this(roomCode, ownerUserId, maxPlayers, players, false);
    }
}
