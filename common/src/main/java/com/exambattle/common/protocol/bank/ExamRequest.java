package com.exambattle.common.protocol.bank;

/** CREATE_EXAM（examId 忽略）與 UPDATE_EXAM（examId 必填）。year、examType 選填。 */
public record ExamRequest(Long examId, String title, Long subjectId, Integer year, String examType) {
}
