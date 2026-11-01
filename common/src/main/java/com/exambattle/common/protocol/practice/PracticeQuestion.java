package com.exambattle.common.protocol.practice;

import com.exambattle.common.protocol.bank.QuestionType;

import java.util.List;

/** 作答時看到的題目（不含答案與解說）；index 從 0 開始。 */
public record PracticeQuestion(int index, long questionId, QuestionType type, String content,
                               List<PracticeOption> options) {
}
