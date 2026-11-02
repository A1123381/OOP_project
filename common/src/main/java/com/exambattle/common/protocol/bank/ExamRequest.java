package com.exambattle.common.protocol.bank;

/**
 * CREATE_EXAM（examId 忽略）與 UPDATE_EXAM（examId 必填）。year、examType 選填。
 * visibility（第八階段）：CREATE_EXAM 為 null 時是私人；UPDATE_EXAM 為 null 時不變。
 */
public record ExamRequest(Long examId, String title, Long subjectId, Integer year, String examType,
                          Visibility visibility) {

    public ExamRequest(Long examId, String title, Long subjectId, Integer year, String examType) {
        this(examId, title, subjectId, year, examType, null);
    }
}
