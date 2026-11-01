package com.exambattle.common.protocol.practice;

import com.exambattle.common.protocol.bank.QuestionData;

/**
 * 結果中的一題：question 為作答當時的題目快照（含正確答案與解說），answer 為你的作答。
 *
 * @param score      本題得分（0–1）
 * @param answeredAt 練習模式中送出該題的時間；考試模式為 null
 */
public record ResultItem(int index, QuestionData question, AnswerData answer, double score, boolean correct,
                         String answeredAt) {
}
