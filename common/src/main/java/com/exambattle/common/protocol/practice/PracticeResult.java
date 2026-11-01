package com.exambattle.common.protocol.practice;

import java.util.List;

/** PRACTICE_RESULT：交卷結果或歷史紀錄的詳細內容。 */
public record PracticeResult(RecordSummary record, List<ResultItem> questions) {
}
