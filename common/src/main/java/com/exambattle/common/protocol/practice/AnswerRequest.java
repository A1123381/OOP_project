package com.exambattle.common.protocol.practice;

import java.util.List;

/** ANSWER_QUESTION（只限練習模式）。 */
public record AnswerRequest(String practiceId, Integer index, List<Long> selectedOptionIds, Boolean trueFalseAnswer) {
}
