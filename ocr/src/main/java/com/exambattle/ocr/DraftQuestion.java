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
 * @param groupIndex  所屬題組在 {@link SplitResult#groups()} 中的位置；不屬於題組時為 null
 * @param unsupported 非選擇題（填充、簡答、作文等），系統不支援，預設不匯入
 * @param imageOptions 選項看起來是圖片或圖表（D-045）：選項文字已清空，由客戶端依座標自動截圖
 */
public record DraftQuestion(Integer number, String content, List<String> options, QuestionType guessedType,
                            int sourcePage, List<String> warnings, Integer groupIndex, boolean unsupported,
                            boolean imageOptions) {

    public DraftQuestion(Integer number, String content, List<String> options, QuestionType guessedType,
                         int sourcePage, List<String> warnings) {
        this(number, content, options, guessedType, sourcePage, warnings, null, false, false);
    }

    public DraftQuestion(Integer number, String content, List<String> options, QuestionType guessedType,
                         int sourcePage, List<String> warnings, Integer groupIndex, boolean unsupported) {
        this(number, content, options, guessedType, sourcePage, warnings, groupIndex, unsupported, false);
    }
}
