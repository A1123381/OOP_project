package com.exambattle.common.protocol.bank;

/**
 * LIST_EXAMS：subjectId 為 null 表示全部。
 * keyword（第八階段）：搜尋考卷名稱、考試類型、年份與建立者，不分大小寫；null 或空白表示不搜尋。
 * mineOnly：只列出自己建立的考卷。
 */
public record ListExamsRequest(Long subjectId, String keyword, Boolean mineOnly) {

    public ListExamsRequest(Long subjectId) {
        this(subjectId, null, null);
    }
}
