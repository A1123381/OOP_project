package com.exambattle.server.dao;

import com.exambattle.server.dao.BankRecords.ExamRecord;
import com.exambattle.server.dao.BankRecords.ExamStats;
import com.exambattle.server.dao.BankRecords.NewExam;

import java.util.List;
import java.util.Optional;

/** exams 資料表的存取介面。 */
public interface ExamDao {

    ExamRecord create(NewExam exam);

    /** 更新名稱、分類、年份、類型（不改建立者）。 */
    void update(ExamRecord exam);

    /** 刪除考卷，並一併刪除不再屬於任何考卷的題目（同一個交易）。 */
    void delete(long examId);

    Optional<ExamStats> findById(long examId);

    /** 考卷清單，依建立時間由新到舊；subjectId 為 null 表示全部。 */
    List<ExamStats> list(Long subjectId);
}
