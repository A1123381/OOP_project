package com.exambattle.server.dao;

import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.server.dao.BankRecords.NewOption;
import com.exambattle.server.dao.BankRecords.NewQuestion;
import com.exambattle.server.dao.BankRecords.OptionRecord;
import com.exambattle.server.dao.BankRecords.QuestionRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 以 MariaDB 實作的 {@link QuestionDao}。題目與選項的寫入都在交易內完成。 */
public final class JdbcQuestionDao implements QuestionDao {

    private static final String QUESTION_COLUMNS =
            "q.id, q.type, q.content, q.explanation, q.true_false_answer, q.created_by, q.group_id, g.passage";

    private final Database database;

    public JdbcQuestionDao(Database database) {
        this.database = database;
    }

    @Override
    public QuestionRecord createInExam(long examId, NewQuestion question) {
        return database.inTransaction("新增題目", c -> {
            long id = insertQuestion(c, question, resolveGroup(c, question));
            linkToExam(c, examId, id, nextPosition(c, examId));
            return load(c, List.of(id)).get(0);
        });
    }

    /** 寫入題目與選項，回傳題目 id。groupId 為 null 表示不屬於題組。呼叫端負責交易。 */
    static long insertQuestion(Connection c, NewQuestion question, Long groupId) throws SQLException {
        long id;
        try (PreparedStatement ps = c.prepareStatement("""
                INSERT INTO questions (type, content, explanation, true_false_answer, group_id, created_by)
                VALUES (?, ?, ?, ?, ?, ?)""", Statement.RETURN_GENERATED_KEYS)) {
            bindQuestion(ps, question);
            bindGroup(ps, 5, groupId);
            ps.setLong(6, question.ownerId());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                id = keys.getLong(1);
            }
        }
        insertOptions(c, id, question.options());
        return id;
    }

    /** 考卷最後一題的下一個位置；鎖住該考卷的題目列，避免同時新增時位置重複。 */
    static int nextPosition(Connection c, long examId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT COALESCE(MAX(position), 0) + 1 FROM exam_questions WHERE exam_id = ? FOR UPDATE")) {
            ps.setLong(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    static void linkToExam(Connection c, long examId, long questionId, int position) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO exam_questions (exam_id, question_id, position) VALUES (?, ?, ?)")) {
            ps.setLong(1, examId);
            ps.setLong(2, questionId);
            ps.setInt(3, position);
            ps.executeUpdate();
        }
    }

    @Override
    public QuestionRecord update(long questionId, NewQuestion question) {
        return database.inTransaction("更新題目", c -> {
            List<Long> previousGroup = groupIdsOf(c, List.of(questionId));
            try (PreparedStatement ps = c.prepareStatement("""
                    UPDATE questions SET type = ?, content = ?, explanation = ?, true_false_answer = ?, group_id = ?
                    WHERE id = ?""")) {
                bindQuestion(ps, question);
                bindGroup(ps, 5, resolveGroup(c, question));
                ps.setLong(6, questionId);
                ps.executeUpdate();
            }
            deleteEmptyGroups(c, previousGroup);
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM question_options WHERE question_id = ?")) {
                ps.setLong(1, questionId);
                ps.executeUpdate();
            }
            insertOptions(c, questionId, question.options());
            return load(c, List.of(questionId)).get(0);
        });
    }

    @Override
    public Optional<QuestionRecord> findById(long questionId) {
        return database.run("查詢題目", c -> {
            List<QuestionRecord> found = load(c, List.of(questionId));
            return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
        });
    }

    @Override
    public List<QuestionRecord> findByIds(List<Long> questionIds) {
        return database.run("查詢題目", c -> load(c, questionIds));
    }

    @Override
    public List<Long> answeredIds(Long examId, Long subjectId) {
        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT q.id FROM questions q
                JOIN exam_questions eq ON eq.question_id = q.id
                JOIN exams e ON e.id = eq.exam_id
                WHERE ((q.type = 'TRUE_FALSE' AND q.true_false_answer IS NOT NULL)
                    OR (q.type <> 'TRUE_FALSE' AND EXISTS (SELECT 1 FROM question_options o
                                                          WHERE o.question_id = q.id AND o.is_correct = 1)))""");
        Long filter = null;
        if (examId != null) {
            sql.append(" AND e.id = ?");
            filter = examId;
        } else if (subjectId != null) {
            sql.append(" AND e.subject_id = ?");
            filter = subjectId;
        }
        sql.append(" ORDER BY q.id");
        Long bound = filter;
        return database.run("查詢題目", c -> {
            try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
                if (bound != null) {
                    ps.setLong(1, bound);
                }
                try (ResultSet rs = ps.executeQuery()) {
                    List<Long> ids = new ArrayList<>();
                    while (rs.next()) {
                        ids.add(rs.getLong(1));
                    }
                    return ids;
                }
            }
        });
    }

    @Override
    public List<QuestionRecord> findByExam(long examId) {
        return database.run("查詢題目", c -> load(c, questionIds(c, examId)));
    }

    @Override
    public List<Long> questionIdsOf(long examId) {
        return database.run("查詢題目", c -> questionIds(c, examId));
    }

    @Override
    public boolean isInExam(long examId, long questionId) {
        return database.run("查詢題目", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT 1 FROM exam_questions WHERE exam_id = ? AND question_id = ?")) {
                ps.setLong(1, examId);
                ps.setLong(2, questionId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    @Override
    public void removeFromExam(long examId, long questionId) {
        database.inTransaction("刪除題目", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "DELETE FROM exam_questions WHERE exam_id = ? AND question_id = ?")) {
                ps.setLong(1, examId);
                ps.setLong(2, questionId);
                ps.executeUpdate();
            }
            List<Long> groupIds = groupIdsOf(c, List.of(questionId));
            JdbcExamDao.deleteOrphans(c, List.of(questionId));
            deleteEmptyGroups(c, groupIds);
            return null;
        });
    }

    @Override
    public void reorder(long examId, List<Long> questionIds) {
        database.inTransaction("重新排序", c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE exam_questions SET position = ? WHERE exam_id = ? AND question_id = ?")) {
                for (int i = 0; i < questionIds.size(); i++) {
                    ps.setInt(1, i + 1);
                    ps.setLong(2, examId);
                    ps.setLong(3, questionIds.get(i));
                    ps.addBatch();
                }
                ps.executeBatch();
            }
            return null;
        });
    }

    @Override
    public boolean isGroupInExam(long examId, long groupId) {
        return database.run("查詢題組", c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT 1 FROM exam_questions eq JOIN questions q ON q.id = eq.question_id
                    WHERE eq.exam_id = ? AND q.group_id = ? LIMIT 1""")) {
                ps.setLong(1, examId);
                ps.setLong(2, groupId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
    }

    @Override
    public void updatePassage(long groupId, String passage) {
        database.run("修改題組文章", c -> {
            try (PreparedStatement ps = c.prepareStatement("UPDATE question_groups SET passage = ? WHERE id = ?")) {
                ps.setString(1, passage);
                ps.setLong(2, groupId);
                ps.executeUpdate();
            }
            return null;
        });
    }

    /** 新增題組，回傳 id。呼叫端負責交易。 */
    static long insertGroup(Connection c, String passage, long ownerId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO question_groups (passage, created_by) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, passage);
            ps.setLong(2, ownerId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    /** 依 NewQuestion 的題組欄位決定 group_id：加入既有題組、建立新題組，或 null。 */
    private static Long resolveGroup(Connection c, NewQuestion question) throws SQLException {
        if (question.groupId() != null) {
            return question.groupId();
        }
        return question.newPassage() == null ? null : insertGroup(c, question.newPassage(), question.ownerId());
    }

    /** 這些題目所屬的題組 id（不重複）。 */
    static List<Long> groupIdsOf(Connection c, List<Long> questionIds) throws SQLException {
        if (questionIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(questionIds.size(), "?"));
        try (PreparedStatement ps = c.prepareStatement("SELECT DISTINCT group_id FROM questions WHERE id IN ("
                + placeholders + ") AND group_id IS NOT NULL")) {
            bindIds(ps, questionIds);
            try (ResultSet rs = ps.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
                return ids;
            }
        }
    }

    /** 刪除已經沒有任何題目的題組。 */
    static void deleteEmptyGroups(Connection c, List<Long> groupIds) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("""
                DELETE FROM question_groups WHERE id = ?
                AND NOT EXISTS (SELECT 1 FROM questions WHERE group_id = ?)""")) {
            for (long id : groupIds) {
                ps.setLong(1, id);
                ps.setLong(2, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void bindGroup(PreparedStatement ps, int index, Long groupId) throws SQLException {
        if (groupId == null) {
            ps.setNull(index, Types.BIGINT);
        } else {
            ps.setLong(index, groupId);
        }
    }

    private static List<Long> questionIds(Connection c, long examId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT question_id FROM exam_questions WHERE exam_id = ? ORDER BY position, question_id")) {
            ps.setLong(1, examId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
                return ids;
            }
        }
    }

    /** 依 ids 的順序載入題目與選項；不存在的 id 會被略過。 */
    private static List<QuestionRecord> load(Connection c, List<Long> ids) throws SQLException {
        if (ids.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));

        Map<Long, List<OptionRecord>> options = new HashMap<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT question_id, id, content, is_correct "
                + "FROM question_options WHERE question_id IN (" + placeholders + ") ORDER BY question_id, position")) {
            bindIds(ps, ids);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    options.computeIfAbsent(rs.getLong("question_id"), k -> new ArrayList<>())
                            .add(new OptionRecord(rs.getLong("id"), rs.getString("content"), rs.getBoolean("is_correct")));
                }
            }
        }

        Map<Long, QuestionRecord> byId = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT " + QUESTION_COLUMNS + " FROM questions q LEFT JOIN question_groups g ON g.id = q.group_id"
                        + " WHERE q.id IN (" + placeholders + ")")) {
            bindIds(ps, ids);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    boolean tf = rs.getBoolean("true_false_answer");
                    Boolean trueFalse = rs.wasNull() ? null : tf;
                    long group = rs.getLong("group_id");
                    Long groupId = rs.wasNull() ? null : group;
                    byId.put(id, new QuestionRecord(id, QuestionType.valueOf(rs.getString("type")),
                            rs.getString("content"), rs.getString("explanation"), trueFalse,
                            rs.getLong("created_by"), List.copyOf(options.getOrDefault(id, List.of())),
                            groupId, rs.getString("passage")));
                }
            }
        }
        return ids.stream().map(byId::get).filter(q -> q != null).toList();
    }

    static void bindQuestion(PreparedStatement ps, NewQuestion q) throws SQLException {
        ps.setString(1, q.type().name());
        ps.setString(2, q.content());
        ps.setString(3, q.explanation());
        if (q.trueFalseAnswer() == null) {
            ps.setNull(4, Types.TINYINT);
        } else {
            ps.setBoolean(4, q.trueFalseAnswer());
        }
    }

    static void insertOptions(Connection c, long questionId, List<NewOption> options) throws SQLException {
        if (options.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO question_options (question_id, position, content, is_correct) VALUES (?, ?, ?, ?)")) {
            for (int i = 0; i < options.size(); i++) {
                ps.setLong(1, questionId);
                ps.setInt(2, i + 1);
                ps.setString(3, options.get(i).content());
                ps.setBoolean(4, options.get(i).correct());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private static void bindIds(PreparedStatement ps, List<Long> ids) throws SQLException {
        for (int i = 0; i < ids.size(); i++) {
            ps.setLong(i + 1, ids.get(i));
        }
    }
}
