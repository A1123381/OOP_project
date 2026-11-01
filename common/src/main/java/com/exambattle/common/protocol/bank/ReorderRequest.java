package com.exambattle.common.protocol.bank;

import java.util.List;

/** REORDER_QUESTIONS：questionIds 必須恰好是考卷目前的全部題目。 */
public record ReorderRequest(Long examId, List<Long> questionIds) {
}
