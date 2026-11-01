package com.exambattle.common.protocol.bank;

import java.util.List;

/**
 * 一道題目。送出時 id、answerSet 會被忽略。
 *
 * @param options         選擇題的選項（依順序）；是非題為空清單
 * @param trueFalseAnswer 是非題答案；null 表示未設定。非建立者收到時一律為 null
 * @param answerSet       是否已設定正確答案（由伺服器計算，所有人都看得到）
 */
public record QuestionData(Long id, QuestionType type, String content, String explanation,
                           List<OptionData> options, Boolean trueFalseAnswer, boolean answerSet) {
}
