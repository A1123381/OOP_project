package com.exambattle.server.dao;

import com.exambattle.server.dao.BankRecords.NewQuestion;
import com.exambattle.server.dao.BankRecords.QuestionRecord;

import java.util.List;
import java.util.Optional;

/** questions、question_options、exam_questions 資料表的存取介面。 */
public interface QuestionDao {

    /** 新增題目與選項，並放到考卷最後（同一個交易）。 */
    QuestionRecord createInExam(long examId, NewQuestion question);

    /** 更新題目內容；選項整組取代（選項 id 會改變）。原本的題組若不再有題目就一併刪除。 */
    QuestionRecord update(long questionId, NewQuestion question);

    Optional<QuestionRecord> findById(long questionId);

    /** 依 ids 的順序載入題目；不存在的 id 會被略過。 */
    List<QuestionRecord> findByIds(List<Long> questionIds);

    /**
     * 已設定答案的題目 id（練習、競賽用）。examId 優先；兩者皆為 null 表示全部題庫。
     * viewerId 不為 null 時只取這位使用者看得到的考卷中的題目（第八階段）。
     */
    List<Long> answeredIds(Long examId, Long subjectId, Long viewerId);

    /** 不限可見範圍。 */
    default List<Long> answeredIds(Long examId, Long subjectId) {
        return answeredIds(examId, subjectId, null);
    }

    /** 考卷中的題目，依考卷內的順序。 */
    List<QuestionRecord> findByExam(long examId);

    /** 考卷中題目的 id，依順序。 */
    List<Long> questionIdsOf(long examId);

    boolean isInExam(long examId, long questionId);

    /** 從考卷移除題目；若題目不再屬於任何考卷就一併刪除，題組也不再有題目時一併刪除（同一個交易）。 */
    void removeFromExam(long examId, long questionId);

    /** 依給定順序重新編號；呼叫端須保證 questionIds 恰為考卷的全部題目。 */
    void reorder(long examId, List<Long> questionIds);

    /** 考卷中是否有題目屬於這個題組。 */
    boolean isGroupInExam(long examId, long groupId);

    /** 修改題組文章（同組的題目一起改變）。 */
    void updatePassage(long groupId, String passage);
}
