package com.exambattle.common.protocol.bank;

/** UPDATE_GROUP：修改題組文章，同組的題目一起改變。只有考卷建立者可以。 */
public record GroupRequest(Long examId, Long groupId, String passage) {
}
