package com.exambattle.common.protocol.bank;

import java.util.List;

/** EXAM_DETAIL：考卷與其題目（依順序）。 */
public record ExamDetail(ExamSummary exam, List<QuestionData> questions) {
}
