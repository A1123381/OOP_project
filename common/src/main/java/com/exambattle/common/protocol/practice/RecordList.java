package com.exambattle.common.protocol.practice;

import java.util.List;

/** RECORDS：自己的練習紀錄（新到舊）與統計。 */
public record RecordList(List<RecordSummary> records, PracticeStats stats) {
}
