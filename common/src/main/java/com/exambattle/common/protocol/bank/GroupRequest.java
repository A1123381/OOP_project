package com.exambattle.common.protocol.bank;

/**
 * UPDATE_GROUP：修改題組文章，同組的題目一起改變。只有考卷建立者可以。
 * imageId 是題組文章的圖片（D-043），整個取代：null 表示沒有圖片。
 */
public record GroupRequest(Long examId, Long groupId, String passage, String imageId) {

    public GroupRequest(Long examId, Long groupId, String passage) {
        this(examId, groupId, passage, null);
    }
}
