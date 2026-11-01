package com.exambattle.server.dao;

import com.exambattle.server.dao.BankRecords.NewExam;
import com.exambattle.server.dao.BankRecords.NewQuestion;

import java.util.List;

/** 整批匯入題目（第七階段，見 DECISIONS.md D-031）。 */
public interface ImportDao {

    /**
     * 在同一個交易內：newExam 不為 null 時先建立考卷，再把全部題目依序加到考卷最後。
     * 任何一步失敗就全部回滾（考卷也不會建立）。
     *
     * @param examId  既有考卷；與 newExam 二選一
     * @return 考卷 id
     */
    long importQuestions(Long examId, NewExam newExam, List<NewQuestion> questions);
}
