package com.exambattle.common.protocol.practice;

import com.exambattle.common.protocol.bank.QuestionType;

import java.util.List;

/**
 * 作答時看到的題目（不含答案與解說）；index 從 0 開始。
 * groupId、passage 是所屬題組與其文章（閱讀測驗、克漏字），不屬於題組時為 null。
 */
public record PracticeQuestion(int index, long questionId, QuestionType type, String content,
                               List<PracticeOption> options, Long groupId, String passage) {

    /** 不屬於題組的題目。 */
    public PracticeQuestion(int index, long questionId, QuestionType type, String content,
                            List<PracticeOption> options) {
        this(index, questionId, type, content, options, null, null);
    }
}
