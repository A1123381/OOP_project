package com.exambattle.common.protocol.game;

import com.exambattle.common.protocol.practice.PracticeQuestion;

/**
 * GAME_QUESTION：所有玩家同時收到的題目（不含答案）。
 * durationMs 是作答時間，客戶端只用來顯示倒數；是否逾時一律由伺服器判斷。
 */
public record GameQuestion(String gameId, int index, int total, PracticeQuestion question, long durationMs) {
}
