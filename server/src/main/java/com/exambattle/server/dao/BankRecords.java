package com.exambattle.server.dao;

import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.bank.Visibility;

import java.util.List;

/** 題庫資料表對應的資料型別。 */
public final class BankRecords {

    private BankRecords() {
    }

    /** subjects 的一列。 */
    public record SubjectRecord(long id, String name) {
    }

    /**
     * exams 的一列。year、examType 可為 null。
     * visibility、takenDown：公開範圍與管理員下架（第八階段，D-039、D-040）。
     */
    public record ExamRecord(long id, String title, long subjectId, Integer year, String examType, long ownerId,
                             Visibility visibility, boolean takenDown) {

        /** 公開、未下架的考卷。 */
        public ExamRecord(long id, String title, long subjectId, Integer year, String examType, long ownerId) {
            this(id, title, subjectId, year, examType, ownerId, Visibility.PUBLIC, false);
        }

        /** 這位使用者能不能看到（瀏覽、練習、競賽）：建立者，或公開且未下架。 */
        public boolean visibleTo(long userId) {
            return ownerId == userId || visibility == Visibility.PUBLIC && !takenDown;
        }
    }

    /** 新增考卷用（尚未有 id）。 */
    public record NewExam(String title, long subjectId, Integer year, String examType, long ownerId,
                          Visibility visibility) {

        public NewExam(String title, long subjectId, Integer year, String examType, long ownerId) {
            this(title, subjectId, year, examType, ownerId, Visibility.PUBLIC);
        }
    }

    /**
     * 考卷清單的條件（第八階段）。
     *
     * @param subjectId        null 表示全部分類
     * @param keyword          搜尋考卷名稱、考試類型、年份、建立者帳號與名稱；null 表示不搜尋
     * @param viewerId         只列出這位使用者看得到的考卷；null 表示不限制（管理工具、測試用）
     * @param includeTakenDown 也列出被下架的考卷（管理員）
     * @param mineOnly         只列出 viewerId 建立的考卷
     */
    public record ExamQuery(Long subjectId, String keyword, Long viewerId, boolean includeTakenDown, boolean mineOnly) {
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
