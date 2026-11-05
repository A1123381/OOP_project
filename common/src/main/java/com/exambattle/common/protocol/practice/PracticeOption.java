package com.exambattle.common.protocol.practice;

/** 作答時看到的選項（不含是否正確）；imageId 是選項圖片，沒有時為 null。 */
public record PracticeOption(long id, String content, String imageId) {

    public PracticeOption(long id, String content) {
        this(id, content, null);
    }
}
