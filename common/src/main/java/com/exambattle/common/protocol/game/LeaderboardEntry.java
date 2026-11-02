package com.exambattle.common.protocol.game;

import com.exambattle.common.protocol.payload.UserInfo;

/** 排行榜的一列；同分同名次。lastPoints 是最近一題的得分。 */
public record LeaderboardEntry(int rank, UserInfo player, int score, int correctCount, int streak, int lastPoints) {
}
