package com.exambattle.common.protocol.practice;

/**
 * 一次練習的紀錄摘要。score 為 0–100 分（小數 1 位）；correctCount 只算全對的題目。
 * 時間為 ISO-8601 UTC。
 */
public record RecordSummary(long id, PracticeMode mode, RevealMode revealMode, String title, String startedAt,
                            String finishedAt, long durationMs, int questionCount, int correctCount, double score) {
}
