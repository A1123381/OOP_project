package com.exambattle.common.protocol.bank;

/** GROUP_SAVED：修改後的題組文章與圖片。 */
public record GroupSaved(long examId, long groupId, String passage, String imageId) {

    public GroupSaved(long examId, long groupId, String passage) {
        this(examId, groupId, passage, null);
    }
}
