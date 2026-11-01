package com.exambattle.common.protocol.bank;

import com.exambattle.common.protocol.payload.UserInfo;

/**
 * 考卷基本資料；也是 EXAM_SAVED 的 payload。
 *
 * @param questionCount   題目數
 * @param unansweredCount 尚未設定正確答案的題目數
 */
public record ExamSummary(long id, String title, SubjectInfo subject, Integer year, String examType,
                          UserInfo owner, int questionCount, int unansweredCount) {
}
