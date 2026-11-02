package com.exambattle.common.protocol.game;

import java.util.List;

/**
 * QUESTION_RESULT：一題結束時送給每位玩家（you 為各自的結果），附正確答案與排行榜。
 * nextInMs 毫秒後送出下一題或 GAME_OVER。
 */
public record QuestionResult(String gameId, int index, int total, List<Long> correctOptionIds, Boolean correctTrueFalse,
                             String explanation, PlayerResult you, List<LeaderboardEntry> leaderboard, long nextInMs) {
}
