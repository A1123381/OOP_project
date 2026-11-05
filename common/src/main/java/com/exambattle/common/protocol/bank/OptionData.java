package com.exambattle.common.protocol.bank;

/**
 * 選擇題的一個選項。
 *
 * @param id      新增時為 null
 * @param content 選項文字；有圖片時可以是空字串
 * @param correct 是否為正確答案；非考卷建立者收到時為 null（不提供答案）
 * @param imageId 選項圖片（D-043）；null 表示沒有
 */
public record OptionData(Long id, String content, Boolean correct, String imageId) {

    public OptionData(Long id, String content, Boolean correct) {
        this(id, content, correct, null);
    }
}
