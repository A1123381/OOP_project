package com.exambattle.common.protocol.bank;

import com.exambattle.common.protocol.payload.UserInfo;

/**
 * 考卷基本資料；也是 EXAM_SAVED 的 payload。
 *
 * @param questionCount   題目數
 * @param unansweredCount 尚未設定正確答案的題目數
 * @param visibility      公開範圍（第八階段）
 * @param takenDown       被管理員下架：此時一定是私人，建立者不能再公開
 */
public record ExamSummary(long id, String title, SubjectInfo subject, Integer year, String examType,
                          UserInfo owner, int questionCount, int unansweredCount, Visibility visibility,
                          boolean takenDown) {

    /** 公開、未下架的考卷。 */
    public ExamSummary(long id, String title, SubjectInfo subject, Integer year, String examType,
                       UserInfo owner, int questionCount, int unansweredCount) {
        this(id, title, subject, year, examType, owner, questionCount, unansweredCount, Visibility.PUBLIC, false);
    }
}
