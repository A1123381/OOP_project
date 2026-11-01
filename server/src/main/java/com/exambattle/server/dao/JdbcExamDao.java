package com.exambattle.server.dao;

import com.exambattle.server.dao.BankRecords.ExamRecord;
import com.exambattle.server.dao.BankRecords.ExamStats;
import com.exambattle.server.dao.BankRecords.NewExam;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 以 MariaDB 實作的 {@link ExamDao}。 */
public final class JdbcExamDao implements ExamDao {

    /** 考卷與題數統計；「未設答案」的判斷與 QuestionRecord.answerSet() 一致。 */
    private static final String SELECT_STATS = """
            SELECT e.id, e.title, e.subject_id, e.exam_year, e.exam_type, e.created_by,
                   COUNT(q.id) AS question_count,
                   COALESCE(SUM(CASE
                       WHEN q.id IS NULL THEN 0
                       WHEN q.type = 'TRUE_FALSE' THEN q.true_false_answer IS NULL
                       ELSE NOT EXISTS (SELECT 1 FROM question_options o
                                        WHERE o.question_id = q.id AND o.is_correct = 1)
                   END), 0) AS unanswered_count
            FROM exams e
            LEFT JOIN exam_questions eq ON eq.exam_id = e.id
            LEFT JOIN questions q ON q.id = eq.question_id
            """;

    private static final String GROUP_BY = """
             GROUP BY e.id, e.title, e.subject_id, e.exam_year, e.exam_type, e.created_by, e.created_at
            """;

    private final Database database;

    public JdbcExamDao(Database database) {
        this.database = database;
    }

    @Override
    public ExamRecord create(NewExam exam) {
        return database.run("新增考卷", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO exams (title, subject_id, exam_year, exam_type, created_by) VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, exam.title());
                ps.setLong(2, exam.subjectId());
                setNullableInt(ps, 3, exam.year());
                ps.setString(4, exam.examType());
                ps.setLong(5, exam.ownerId());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    return new ExamRecord(keys.getLong(1), exam.title(), exam.subjectId(), exam.year(),
                            exam.examType(), exam.ownerId());
                }
            }
        });
    }

    @Override
    public void update(ExamRecord exam) {
        database.run("更新考卷", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE exams SET title = ?, subject_id = ?, exam_year = ?, exam_type = ? WHERE id = ?")) {
                ps.setString(1, exam.title());
                ps.setLong(2, exam.subjectId());
                setNullableInt(ps, 3, exam.year());
                ps.setString(4, exam.examType());
                ps.setLong(5, exam.id());
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public void delete(long examId) {
        database.inTransaction("刪除考卷", c -> {
            List<Long> questionIds = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT question_id FROM exam_questions WHERE exam_id = ?")) {
                ps.setLong(1, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        questionIds.add(rs.getLong(1));
                    }
                }
            }
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM exams WHERE id = ?")) {
                ps.setLong(1, examId);
                ps.executeUpdate();
            }
            deleteOrphans(c, questionIds);
            return null;
        });
    }

    /** 刪除不再屬於任何考卷的題目；選項由 ON DELETE CASCADE 一併刪除。 */
    static void deleteOrphans(Connection c, List<Long> questionIds) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                DELETE FROM questions WHERE id = ?
                AND NOT EXISTS (SELECT 1 FROM exam_questions WHERE question_id = ?)""")) {
            for (long id : questionIds) {
                ps.setLong(1, id);
                ps.setLong(2, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @Override
    public Optional<ExamStats> findById(long examId) {
        return database.run("查詢考卷", c -> {
            try (PreparedStatement ps = c.prepareStatement(SELECT_STATS + " WHERE e.id = ?" + GROUP_BY)) {
                ps.setLong(1, examId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.of(readStats(rs)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public List<ExamStats> list(Long subjectId) {
        String sql = SELECT_STATS + (subjectId == null ? "" : " WHERE e.subject_id = ?") + GROUP_BY
                + " ORDER BY e.created_at DESC, e.id DESC";
        return database.run("查詢考卷", c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (subjectId != null) {
                    ps.setLong(1, subjectId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    List<ExamStats> exams = new ArrayList<>();
                    while (rs.next()) {
                        exams.add(readStats(rs));
                    }
                    return exams;
                }
            }
        });
    }

    private static ExamStats readStats(ResultSet rs) throws SQLException {
        int year = rs.getInt("exam_year");
        Integer nullableYear = rs.wasNull() ? null : year;
        ExamRecord exam = new ExamRecord(rs.getLong("id"), rs.getString("title"), rs.getLong("subject_id"),
                nullableYear, rs.getString("exam_type"), rs.getLong("created_by"));
        return new ExamStats(exam, rs.getInt("question_count"), rs.getInt("unanswered_count"));
    }

    private static void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.SMALLINT);
        } else {
            ps.setInt(index, value);
        }
    }
}
