package com.exambattle.common.protocol.game;

/** KICK_PLAYER（房主，競賽開始前）：把玩家移出房間。 */
public record KickRequest(Long userId) {
}
