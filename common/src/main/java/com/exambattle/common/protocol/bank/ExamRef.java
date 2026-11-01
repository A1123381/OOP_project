package com.exambattle.common.protocol.bank;

/** 指定一份考卷：GET_EXAM、DELETE_EXAM 的請求，以及 EXAM_DELETED 的回應。 */
public record ExamRef(Long examId) {
}
