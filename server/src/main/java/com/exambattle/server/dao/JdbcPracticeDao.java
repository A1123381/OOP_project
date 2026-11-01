package com.exambattle.server.dao;

import com.exambattle.common.protocol.practice.PracticeMode;
import com.exambattle.common.protocol.practice.RevealMode;
import com.exambattle.server.dao.PracticeRecords.AnswerRow;
import com.exambattle.server.dao.PracticeRecords.NewAnswer;
import com.exambattle.server.dao.PracticeRecords.NewRecord;
import com.exambattle.server.dao.PracticeRecords.RecordDetail;
import com.exambattle.server.dao.PracticeRecords.RecordRow;
import com.exambattle.server.dao.PracticeRecords.StatsRow;
import com.exambattle.server.dao.PracticeRecords.WrongRow;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 以 MariaDB 實作的 {@link PracticeDao} 與 {@link WrongQuestionDao}。
 * 時間一律以 UTC 的 LocalDateTime 寫入 DATETIME(3)，避免時區轉換。
 */
public final class JdbcPracticeDao implements PracticeDao, WrongQuestionDao {

    private static final String RECORD_COLUMNS = "id, user_id, mode, reveal_mode, title, question_count, "
            + "correct_count, score, started_at, finished_at, duration_ms";

    private final Database database;

    public JdbcPracticeDao(Database database) {
        this.database = database;
    }

    @Override
    public long save(NewRecord r, List<Long> wrongQuestionIds) {
        return database.inTransaction("儲存練習紀錄", c -> {
            long recordId;
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO practice_records (user_id, mode, reveal_mode, title, source_exam_id, source_subject_id,
                        question_count, correct_count, score, started_at, finished_at, duration_ms)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS)) {
                ps.setLong(1, r.userId());
                ps.setString(2, r.mode().name());
                ps.setString(3, r.revealMode().name());
                ps.setString(4, r.title());
                setNullableLong(ps, 5, r.sourceExamId());
                setNullableLong(ps, 6, r.sourceSubjectId());
                ps.setInt(7, r.questionCount());
                ps.setInt(8, r.correctCount());
                ps.setDouble(9, r.score());
                ps.setObject(10, utc(r.startedAt()));
                ps.setObject(11, utc(r.finishedAt()));
                ps.setLong(12, r.finishedAt().toEpochMilli() - r.startedAt().toEpochMilli());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    recordId = keys.getLong(1);
                }
            }

            // 題目可能已被刪除：以子查詢取得 id，不存在時寫入 NULL，不會違反外鍵
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO answer_records (record_id, position, question_id, question_snapshot, answer, score,
                        is_correct, answered_at)
                    VALUES (?, ?, (SELECT id FROM questions WHERE id = ?), ?, ?, ?, ?, ?)""")) {
                for (NewAnswer a : r.answers()) {
                    ps.setLong(1, recordId);
                    ps.setInt(2, a.position());
                    ps.setLong(3, a.questionId());
                    ps.setString(4, a.snapshotJson());
                    ps.setString(5, a.answerJson());
                    ps.setDouble(6, a.score());
                    ps.setBoolean(7, a.correct());
                    if (a.answeredAt() == null) {
                        ps.setNull(8, Types.TIMESTAMP);
                    } else {
                        ps.setObject(8, utc(a.answeredAt()));
                    }
                    ps.addBatch();
                }
                ps.executeBatch();
            }

            // 錯題本：題目已刪除時 SELECT 不到資料，自然略過。
            // MariaDB 驅動的批次（bulk）協定不支援 INSERT … SELECT … ON DUPLICATE KEY，所以逐筆執行（最多 100 題）。
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO wrong_questions (user_id, question_id, wrong_count, first_wrong_at, last_wrong_at)
                    SELECT ?, id, 1, ?, ? FROM questions WHERE id = ?
                    ON DUPLICATE KEY UPDATE wrong_count = wrong_count + 1, last_wrong_at = VALUES(last_wrong_at)""")) {
                LocalDateTime now = utc(r.finishedAt());
                for (long questionId : wrongQuestionIds) {
                    ps.setLong(1, r.userId());
                    ps.setObject(2, now);
                    ps.setObject(3, now);
                    ps.setLong(4, questionId);
                    ps.executeUpdate();
                }
            }
            return recordId;
        });
    }

    @Override
    public List<RecordRow> list(long userId) {
        return database.run("查詢練習紀錄", c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT " + RECORD_COLUMNS
                    + " FROM practice_records WHERE user_id = ? ORDER BY finished_at DESC, id DESC")) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    List<RecordRow> rows = new ArrayList<>();
                    while (rs.next()) {
                        rows.add(readRecord(rs));
                    }
                    return rows;
                }
            }
        });
    }

    @Override
    public Optional<RecordDetail> find(long recordId) {
        return database.run("查詢練習紀錄", c -> {
            RecordRow record;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT " + RECORD_COLUMNS + " FROM practice_records WHERE id = ?")) {
                ps.setLong(1, recordId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    record = readRecord(rs);
                }
            }
            List<AnswerRow> answers = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT position, question_id, question_snapshot, answer, score, is_correct, answered_at
                    FROM answer_records WHERE record_id = ? ORDER BY position""")) {
                ps.setLong(1, recordId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long questionId = rs.getLong("question_id");
                        Long nullableId = rs.wasNull() ? null : questionId;
                        answers.add(new AnswerRow(rs.getInt("position"), nullableId, rs.getString("question_snapshot"),
                                rs.getString("answer"), rs.getDouble("score"), rs.getBoolean("is_correct"),
                                instant(rs.getObject("answered_at", LocalDateTime.class))));
                    }
                }
            }
            return Optional.of(new RecordDetail(record, answers));
        });
    }

    @Override
    public StatsRow stats(long userId) {
        return database.run("查詢個人統計", c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT COUNT(*), COALESCE(SUM(question_count), 0), COALESCE(SUM(correct_count), 0),
                           COALESCE(AVG(score), 0)
                    FROM practice_records WHERE user_id = ?""")) {
                ps.setLong(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    return new StatsRow(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getDouble(4));
                }
            }
        });
    }

    @Override
    public List<WrongRow> list(long userId, Long subjectId) {
        // 題目可能屬於多份考卷，取 id 最小的那份作為顯示用的考卷與分類
        String sql = """
                SELECT w.question_id, w.wrong_count, w.last_wrong_at, e.title, s.id AS subject_id, s.name
                FROM wrong_questions w
                LEFT JOIN exams e ON e.id = (SELECT MIN(eq.exam_id) FROM exam_questions eq
                                             WHERE eq.question_id = w.question_id)
                LEFT JOIN subjects s ON s.id = e.subject_id
                WHERE w.user_id = ?""" + (subjectId == null ? "" : " AND e.subject_id = ?")
                + " ORDER BY w.last_wrong_at DESC, w.question_id DESC";
        return database.run("查詢錯題本", c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setLong(1, userId);
                if (subjectId != null) {
                    ps.setLong(2, subjectId);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    List<WrongRow> rows = new ArrayList<>();
                    while (rs.next()) {
                        long sid = rs.getLong("subject_id");
                        Long nullableSubject = rs.wasNull() ? null : sid;
                        rows.add(new WrongRow(rs.getLong("question_id"), rs.getInt("wrong_count"),
                                instant(rs.getObject("last_wrong_at", LocalDateTime.class)), rs.getString("title"),
                                nullableSubject, rs.getString("name")));
                    }
                    return rows;
                }
            }
        });
    }

    @Override
    public boolean remove(long userId, long questionId) {
        return database.run("移除錯題", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM wrong_questions WHERE user_id = ? AND question_id = ?")) {
                ps.setLong(1, userId);
                ps.setLong(2, questionId);
                return ps.executeUpdate() > 0;
            }
        });
    }

    private static RecordRow readRecord(ResultSet rs) throws SQLException {
        return new RecordRow(rs.getLong("id"), rs.getLong("user_id"), PracticeMode.valueOf(rs.getString("mode")),
                RevealMode.valueOf(rs.getString("reveal_mode")), rs.getString("title"), rs.getInt("question_count"),
                rs.getInt("correct_count"), rs.getDouble("score"),
                instant(rs.getObject("started_at", LocalDateTime.class)),
                instant(rs.getObject("finished_at", LocalDateTime.class)), rs.getLong("duration_ms"));
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static Instant instant(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, value);
        }
    }
}
