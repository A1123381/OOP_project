package com.exambattle.common.protocol.practice;

import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.SubjectInfo;

/**
 * 錯題本中的一題。question 為目前的題目內容（含正確答案與解說）；
 * subject、examTitle 取自題目所屬的考卷，可能為 null。
 */
public record WrongItem(long questionId, SubjectInfo subject, String examTitle, QuestionData question,
                        int wrongCount, String lastWrongAt) {
}
