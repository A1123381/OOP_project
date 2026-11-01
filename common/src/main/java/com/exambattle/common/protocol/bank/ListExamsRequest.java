package com.exambattle.common.protocol.bank;

/** LIST_EXAMS：subjectId 為 null 表示全部。 */
public record ListExamsRequest(Long subjectId) {
}
