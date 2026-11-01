package com.exambattle.common.protocol.bank;

import java.util.List;

/**
 * IMPORT_QUESTIONS：把校正後的題目一次匯入題庫（同一個交易，失敗就全部不匯入）。
 * examId（加到自己的考卷最後）與 newExam（建立新考卷）必須二選一。
 *
 * @param groups       要建立的題組文章；可為 null 或空清單
 * @param groupIndexes 與 questions 一一對應：第 i 題所屬的題組在 groups 中的位置，null 表示不屬於題組。
 *                     整個清單可為 null（全部不屬於題組）。questions 內的 groupId、passage 會被忽略
 */
public record ImportRequest(Long examId, ExamRequest newExam, List<QuestionData> questions,
                            List<String> groups, List<Integer> groupIndexes) {

    /** 沒有題組的匯入。 */
    public ImportRequest(Long examId, ExamRequest newExam, List<QuestionData> questions) {
        this(examId, newExam, questions, null, null);
    }
}
