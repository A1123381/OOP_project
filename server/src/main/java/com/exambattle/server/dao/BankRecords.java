package com.exambattle.server.dao;

import com.exambattle.common.protocol.bank.QuestionType;

import java.util.List;

/** 題庫資料表對應的資料型別。 */
public final class BankRecords {

    private BankRecords() {
    }

    /** subjects 的一列。 */
    public record SubjectRecord(long id, String name) {
    }

    /** exams 的一列。year、examType 可為 null。 */
    public record ExamRecord(long id, String title, long subjectId, Integer year, String examType, long ownerId) {
    }

    /** 新增考卷用（尚未有 id）。 */
    public record NewExam(String title, long subjectId, Integer year, String examType, long ownerId) {
    }

    /** 考卷加上題數統計。unansweredCount 為尚未設定正確答案的題目數。 */
    public record ExamStats(ExamRecord exam, int questionCount, int unansweredCount) {
    }

    /** question_options 的一列（依 position 排序後放在 QuestionRecord 中）。 */
    public record OptionRecord(long id, String content, boolean correct) {
    }

    /**
     * questions 的一列與其選項。trueFalseAnswer 為 null 表示是非題答案未設定。
     * groupId、passage 是所屬題組與其文章（D-034）；不屬於題組時為 null。
     */
    public record QuestionRecord(long id, QuestionType type, String content, String explanation,
                                 Boolean trueFalseAnswer, long ownerId, List<OptionRecord> options,
                                 Long groupId, String passage) {

        /** 不屬於題組的題目。 */
        public QuestionRecord(long id, QuestionType type, String content, String explanation,
                              Boolean trueFalseAnswer, long ownerId, List<OptionRecord> options) {
            this(id, type, content, explanation, trueFalseAnswer, ownerId, options, null, null);
        }

        /** 是否已設定正確答案。 */
        public boolean answerSet() {
            return type == QuestionType.TRUE_FALSE
                    ? trueFalseAnswer != null
                    : options.stream().anyMatch(OptionRecord::correct);
        }
    }

    /** 新增或更新題目用的選項。 */
    public record NewOption(String content, boolean correct) {
    }

    /**
     * 新增或更新題目用的內容（選項會整組取代）。
     * 題組：groupId 不為 null 表示加入既有題組；否則 newPassage 不為 null 表示建立新題組；兩者都是 null 表示不屬於題組。
     */
    public record NewQuestion(QuestionType type, String content, String explanation, Boolean trueFalseAnswer,
                              long ownerId, List<NewOption> options, Long groupId, String newPassage) {

        /** 不屬於題組的題目。 */
        public NewQuestion(QuestionType type, String content, String explanation, Boolean trueFalseAnswer,
                           long ownerId, List<NewOption> options) {
            this(type, content, explanation, trueFalseAnswer, ownerId, options, null, null);
        }

        /** 換成另一個題組。 */
        public NewQuestion withGroup(Long groupId, String newPassage) {
            return new NewQuestion(type, content, explanation, trueFalseAnswer, ownerId, options, groupId, newPassage);
        }
    }
}
