package com.exambattle.common.protocol.game;

/** GAME_PROGRESS：目前這一題已作答的人數。 */
public record GameProgress(String gameId, int index, int answered, int players) {
}
