package com.exambattle.ocr;

import com.exambattle.common.protocol.bank.QuestionType;

import java.util.List;

/**
 * 切題得到的題目草稿，待使用者在第七階段校正後才匯入題庫。
 *
 * @param number      題號；無法判斷時為 null
 * @param guessedType 推測的題型：有 2 個以上選項時為單選；沒有選項時為 null（由使用者決定）
 * @param sourcePage  題目開始的頁碼（從 1 開始）
 * @param warnings    需要人工確認的地方
 */
public record DraftQuestion(Integer number, String content, List<String> options, QuestionType guessedType,
                            int sourcePage, List<String> warnings) {
}
