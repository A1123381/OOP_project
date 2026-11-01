package com.exambattle.common.protocol.bank;

/** GROUP_SAVED：修改後的題組文章。 */
public record GroupSaved(long examId, long groupId, String passage) {
}
