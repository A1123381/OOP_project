package com.exambattle.common.protocol.practice;

import java.util.List;

/** ANSWER_RESULT：本題得分（0–1）與正確答案、解說。 */
public record AnswerResult(int index, boolean correct, double score, List<Long> correctOptionIds,
                           Boolean correctTrueFalse, String explanation) {
}
