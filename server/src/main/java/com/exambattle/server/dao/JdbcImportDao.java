package com.exambattle.server.dao;

import com.exambattle.server.dao.BankRecords.NewExam;
import com.exambattle.server.dao.BankRecords.NewQuestion;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/** 以 MariaDB 實作的 {@link ImportDao}；沿用 JdbcQuestionDao 寫入題目的方法。 */
public final class JdbcImportDao implements ImportDao {

    private final Database database;

    public JdbcImportDao(Database database) {
        this.database = database;
    }

    @Override
    public long importQuestions(Long examId, NewExam newExam, List<NewQuestion> questions, List<String> groups,
                                List<Integer> groupIndexes) {
        return database.inTransaction("匯入題目", c -> {
            long target;
            if (newExam != null) {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO exams (title, subject_id, exam_year, exam_type, created_by, visibility)"
                                + " VALUES (?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, newExam.title());
                    ps.setLong(2, newExam.subjectId());
                    if (newExam.year() == null) {
                        ps.setNull(3, Types.SMALLINT);
                    } else {
                        ps.setInt(3, newExam.year());
                    }
                    ps.setString(4, newExam.examType());
                    ps.setLong(5, newExam.ownerId());
                    ps.setString(6, newExam.visibility().name());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        target = keys.getLong(1);
                    }
                }
            } else {
                target = examId;
            }
            List<Long> groupIds = new ArrayList<>();
            for (String passage : groups) {
                groupIds.add(JdbcQuestionDao.insertGroup(c, passage, questions.get(0).ownerId()));
            }
            int position = JdbcQuestionDao.nextPosition(c, target);
            for (int i = 0; i < questions.size(); i++) {
                Integer index = groupIndexes.get(i);
                long id = JdbcQuestionDao.insertQuestion(c, questions.get(i), index == null ? null : groupIds.get(index));
                JdbcQuestionDao.linkToExam(c, target, id, position++);
            }
            // 沒有被任何題目使用的題組（呼叫端應已排除）不留下
            JdbcQuestionDao.deleteEmptyGroups(c, groupIds);
            return target;
        });
    }
}
