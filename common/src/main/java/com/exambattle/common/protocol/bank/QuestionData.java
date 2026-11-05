package com.exambattle.common.protocol.bank;

import java.util.List;

/**
 * 一道題目。送出時 id、answerSet 會被忽略。
 *
 * @param options         選擇題的選項（依順序）；是非題為空清單
 * @param trueFalseAnswer 是非題答案；null 表示未設定。非建立者收到時一律為 null
 * @param answerSet       是否已設定正確答案（由伺服器計算，所有人都看得到）
 * @param groupId         所屬題組（閱讀測驗、克漏字等共用一篇文章，見 DECISIONS.md D-034）；不屬於題組時為 null。
 *                        ADD_QUESTION／UPDATE_QUESTION 帶入時表示加入同一份考卷中既有的題組
 * @param passage         題組文章；不屬於題組時為 null。ADD_QUESTION／UPDATE_QUESTION 只帶 passage（groupId 為 null）
 *                        時表示建立新題組；兩者都是 null 表示不屬於題組
 * @param imageId         題幹圖片（D-043）；null 表示沒有
 * @param passageImageId  題組文章的圖片；建立新題組時一併設定，加入既有題組時忽略
 */
public record QuestionData(Long id, QuestionType type, String content, String explanation,
                           List<OptionData> options, Boolean trueFalseAnswer, boolean answerSet,
                           Long groupId, String passage, String imageId, String passageImageId) {

    /** 不屬於題組、沒有圖片的題目。 */
    public QuestionData(Long id, QuestionType type, String content, String explanation,
                        List<OptionData> options, Boolean trueFalseAnswer, boolean answerSet) {
        this(id, type, content, explanation, options, trueFalseAnswer, answerSet, null, null, null, null);
    }

    /** 沒有圖片的題目。 */
    public QuestionData(Long id, QuestionType type, String content, String explanation,
                        List<OptionData> options, Boolean trueFalseAnswer, boolean answerSet,
                        Long groupId, String passage) {
        this(id, type, content, explanation, options, trueFalseAnswer, answerSet, groupId, passage, null, null);
    }

    /** 換成另一個題組（或 null 表示不屬於題組）；題組文章圖片一併清除。 */
    public QuestionData withGroup(Long groupId, String passage) {
        return withGroup(groupId, passage, null);
    }

    /** 換成另一個題組，並指定新題組文章的圖片。 */
    public QuestionData withGroup(Long groupId, String passage, String passageImageId) {
        return new QuestionData(id, type, content, explanation, options, trueFalseAnswer, answerSet, groupId, passage,
                imageId, passageImageId);
    }
}
