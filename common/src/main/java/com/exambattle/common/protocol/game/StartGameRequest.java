package com.exambattle.common.protocol.game;

/**
 * START_GAME（房主）：題目來源與每題作答秒數。
 * examId 優先，否則 subjectId，兩者皆為 null 表示全部題庫；只出已設定答案的題目。
 *
 * @param random             true 為隨機抽題；false 為依考卷順序（只在選考卷時有效）
 * @param count              題數上限；null 時隨機抽題為 10 題，依序為全部
 * @param secondsPerQuestion 每題秒數，必須是 {@link com.exambattle.common.protocol.Protocol#GAME_SECONDS_CHOICES} 之一
 */
public record StartGameRequest(Long examId, Long subjectId, boolean random, Integer count, int secondsPerQuestion) {
}
