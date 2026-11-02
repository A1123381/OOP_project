package com.exambattle.common.protocol.game;

import java.util.List;

/**
 * GAME_OVER：最終排行榜。wrongAdded 是這位玩家加入錯題本的題數（各自不同）。
 * questionsPlayed 是已公布結果的題數（提前結束時可能少於 questionCount）。
 */
public record GameOver(String gameId, String title, GameEndReason reason, int questionCount, int questionsPlayed,
                       List<LeaderboardEntry> leaderboard, int wrongAdded) {
}
