package com.exambattle.ocr;

import java.util.List;

/**
 * 切題得到的題組（閱讀測驗、克漏字、文意選填等多題共用一篇文章），見 DECISIONS.md D-035。
 *
 * @param passage     題組文章；克漏字與共用選項題組的空格已標成【11】
 * @param firstNumber 題組標題寫的第一題題號；沒有標題（由長文章推測）時為 null
 * @param lastNumber  題組標題寫的最後一題題號；沒有標題時為 null
 * @param sourcePage  文章開始的頁碼（從 1 開始）
 * @param warnings    需要人工確認的地方
 */
public record DraftGroup(String passage, Integer firstNumber, Integer lastNumber, int sourcePage,
                         List<String> warnings) {
}
