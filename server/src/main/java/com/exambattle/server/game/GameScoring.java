package com.exambattle.server.game;

import com.exambattle.common.protocol.Protocol;

/**
 * 競賽計分（D-037），一律由伺服器計算，不看作答速度：
 * <ul>
 *   <li>本題分數 = 基本分 1000 × 得分比例（單選、是非為 0 或 1；多選依學測規則部分給分，見 Grader）</li>
 *   <li>全對才算答對，連續答對題數加 1；其餘（含部分給分、未作答）連續答對歸零</li>
 *   <li>連續答對加成：第 2 題起每多連續 1 題加 100 分，最多 500 分；只加在答對的題目</li>
 * </ul>
 */
public final class GameScoring {

    /** 一題的計分結果。 */
    public record Points(int base, int bonus, int streak) {
        public int total() {
            return base + bonus;
        }
    }

    private GameScoring() {
    }

    /**
     * @param score        本題得分比例（0–1）
     * @param streakBefore 作答前的連續答對題數
     */
    public static Points points(double score, int streakBefore) {
        int base = (int) Math.round(Protocol.GAME_BASE_POINTS * score);
        if (score < 1.0) {
            return new Points(base, 0, 0);
        }
        int streak = streakBefore + 1;
        int bonus = Math.min((streak - 1) * Protocol.GAME_STREAK_STEP, Protocol.GAME_STREAK_MAX_BONUS);
        return new Points(base, bonus, streak);
    }
}
