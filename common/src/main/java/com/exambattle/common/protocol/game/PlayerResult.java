package com.exambattle.common.protocol.game;

/**
 * 自己在這一題的結果。
 *
 * @param score  0–1（多選依學測規則部分給分）
 * @param points 本題得分（含連續答對加成）
 * @param bonus  其中的連續答對加成
 * @param streak 目前連續答對的題數
 */
public record PlayerResult(boolean answered, double score, boolean correct, int points, int bonus, int streak) {
}
