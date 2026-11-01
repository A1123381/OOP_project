package com.exambattle.common.protocol.bank;

/**
 * 選擇題的一個選項。
 *
 * @param id      新增時為 null
 * @param correct 是否為正確答案；非考卷建立者收到時為 null（不提供答案）
 */
public record OptionData(Long id, String content, Boolean correct) {
}
