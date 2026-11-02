package com.exambattle.server.dao;

import com.exambattle.server.dao.PracticeRecords.WrongRow;

import java.time.Instant;
import java.util.List;

/** wrong_questions 的存取介面。單人練習的錯題由 {@link PracticeDao#save} 在交卷時加入。 */
public interface WrongQuestionDao {

    /** 加入錯題本（已存在則累計次數）；已被刪除的題目略過。多人競賽結束時使用。 */
    void add(long userId, List<Long> questionIds, Instant at);

    /** 錯題本，依最近答錯時間由新到舊；subjectId 為 null 表示全部。 */
    List<WrongRow> list(long userId, Long subjectId);

    /** 手動移除；回傳是否有移除。 */
    boolean remove(long userId, long questionId);
}
