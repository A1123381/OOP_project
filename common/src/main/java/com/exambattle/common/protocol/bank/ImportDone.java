package com.exambattle.common.protocol.bank;

/** IMPORT_DONE：匯入後的考卷與本次匯入的題數。 */
public record ImportDone(ExamSummary exam, int importedCount) {
}
