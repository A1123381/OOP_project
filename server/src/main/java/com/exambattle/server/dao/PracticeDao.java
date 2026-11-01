package com.exambattle.server.dao;

import com.exambattle.server.dao.PracticeRecords.NewRecord;
import com.exambattle.server.dao.PracticeRecords.RecordDetail;
import com.exambattle.server.dao.PracticeRecords.RecordRow;
import com.exambattle.server.dao.PracticeRecords.StatsRow;

import java.util.List;
import java.util.Optional;

/** practice_records、answer_records 的存取介面。 */
public interface PracticeDao {

    /**
     * 在同一個交易內寫入紀錄、全部作答，並把 wrongQuestionIds 加入錯題本（已存在則累計次數）。
     * 已被刪除的題目不會加入錯題本，作答的 question_id 會是 null。
     *
     * @return 新紀錄的 id
     */
    long save(NewRecord record, List<Long> wrongQuestionIds);

    /** 使用者的紀錄，依交卷時間由新到舊。 */
    List<RecordRow> list(long userId);

    Optional<RecordDetail> find(long recordId);

    StatsRow stats(long userId);
}
