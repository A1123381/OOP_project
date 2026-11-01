package com.exambattle.client.gui.model;

import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.bank.ExamRequest;
import com.exambattle.common.protocol.bank.SubjectInfo;

import java.util.Optional;

/**
 * 考卷表單的內容。檢查規則與伺服器相同，只為了提早提示；最終以伺服器為準。
 *
 * @param yearText 使用者輸入的年份文字，可空白
 */
public record ExamDraft(String title, SubjectInfo subject, String yearText, String examType) {

    /** 回傳第一個錯誤訊息；沒有錯誤時為 empty。 */
    public Optional<String> validate() {
        String t = title == null ? "" : title.strip();
        if (t.isEmpty()) {
            return Optional.of("請輸入考卷名稱");
        }
        if (t.length() > Protocol.MAX_EXAM_TITLE_CHARS) {
            return Optional.of("考卷名稱不可超過 " + Protocol.MAX_EXAM_TITLE_CHARS + " 字元");
        }
        if (subject == null) {
            return Optional.of("請選擇分類");
        }
        if (!isBlank(yearText)) {
            try {
                int year = Integer.parseInt(yearText.strip());
                if (year < Protocol.MIN_EXAM_YEAR || year > Protocol.MAX_EXAM_YEAR) {
                    return Optional.of("考試年份必須介於 " + Protocol.MIN_EXAM_YEAR + "–" + Protocol.MAX_EXAM_YEAR);
                }
            } catch (NumberFormatException e) {
                return Optional.of("考試年份必須是數字（西元年）");
            }
        }
        if (examType != null && examType.strip().length() > Protocol.MAX_EXAM_TYPE_CHARS) {
            return Optional.of("考試類型不可超過 " + Protocol.MAX_EXAM_TYPE_CHARS + " 字元");
        }
        return Optional.empty();
    }

    /** 轉成請求；呼叫前須先通過 {@link #validate()}。examId 為 null 表示新增。 */
    public ExamRequest toRequest(Long examId) {
        Integer year = isBlank(yearText) ? null : Integer.parseInt(yearText.strip());
        String type = isBlank(examType) ? null : examType.strip();
        return new ExamRequest(examId, title.strip(), subject.id(), year, type);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
