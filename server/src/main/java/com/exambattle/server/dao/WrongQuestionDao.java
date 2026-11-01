package com.exambattle.server.dao;

import com.exambattle.server.dao.PracticeRecords.WrongRow;

import java.util.List;

/** wrong_questions 的存取介面。加入錯題由 {@link PracticeDao#save} 在交卷時完成。 */
public interface WrongQuestionDao {

    /** 錯題本，依最近答錯時間由新到舊；subjectId 為 null 表示全部。 */
    List<WrongRow> list(long userId, Long subjectId);

    /** 手動移除；回傳是否有移除。 */
    boolean remove(long userId, long questionId);
}
