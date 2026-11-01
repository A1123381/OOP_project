package com.exambattle.common.protocol.practice;

/**
 * 個人答題統計。accuracy 為答對題數 ÷ 總題數（0–1）；averageScore 為各次練習分數的平均。
 */
public record PracticeStats(int practiceCount, int questionCount, int correctCount, double accuracy,
                            double averageScore) {
}
