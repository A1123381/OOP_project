package com.exambattle.common.protocol.bank;

import java.util.List;

/**
 * IMPORT_QUESTIONS：把校正後的題目一次匯入題庫（同一個交易，失敗就全部不匯入）。
 * examId（加到自己的考卷最後）與 newExam（建立新考卷）必須二選一。
 */
public record ImportRequest(Long examId, ExamRequest newExam, List<QuestionData> questions) {
}
