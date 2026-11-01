package com.exambattle.common.protocol.bank;

import java.util.List;

/** EXAMS：考卷清單，依建立時間由新到舊。 */
public record ExamList(List<ExamSummary> exams) {
}
