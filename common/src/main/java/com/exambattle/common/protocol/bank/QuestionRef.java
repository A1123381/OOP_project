package com.exambattle.common.protocol.bank;

/** DELETE_QUESTION 的請求與 QUESTION_DELETED 的回應。 */
public record QuestionRef(Long examId, Long questionId) {
}
