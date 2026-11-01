package com.exambattle.server.dao;

import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.server.dao.BankRecords.NewExam;
import com.exambattle.server.dao.BankRecords.NewOption;
import com.exambattle.server.dao.BankRecords.NewQuestion;
import com.exambattle.server.dao.BankRecords.QuestionRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 對真正的 MariaDB 測試整批匯入的交易：成功時題目依序寫入，失敗時全部回滾（考卷也不會建立）。
 * 只有設定 EXAMBATTLE_TEST_DB_URL 時才執行；只刪除本測試建立的資料。
 */
@EnabledIfEnvironmentVariable(named = "EXAMBATTLE_TEST_DB_URL", matches = ".+")
class JdbcImportDaoTest {

    private Database database;
    private long userId;
    private long subjectId;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(new DbConfig(System.getenv("EXAMBATTLE_TEST_DB_URL"),
                System.getenv("EXAMBATTLE_TEST_DB_USER"),
                System.getenv().getOrDefault("EXAMBATTLE_TEST_DB_PASSWORD", "")));
        database.verify();
        String prefix = "t_" + UUID.randomUUID().toString().substring(0, 8) + "_";
        userId = new JdbcUserDao(database).create(new NewUser(prefix + "u", "Tester", new byte[32], new byte[16], 1)).id();
        subjectId = new JdbcSubjectDao(database).create(prefix + "科目", userId).id();
    }

    @AfterEach
    void cleanUp() throws Exception {
        try (Connection c = database.connection()) {
            for (String sql : List.of("DELETE FROM exams WHERE created_by = ?", "DELETE FROM questions WHERE created_by = ?",
                    "DELETE FROM question_groups WHERE created_by = ?",
                    "DELETE FROM subjects WHERE created_by = ?", "DELETE FROM users WHERE id = ?")) {
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setLong(1, userId);
                    ps.executeUpdate();
                }
            }
        }
    }

    private NewQuestion question(String content) {
        return new NewQuestion(QuestionType.SINGLE_CHOICE, content, null, null, userId,
                List.of(new NewOption("A", true), new NewOption("B", false)));
    }

    @Test
    void importsIntoNewAndExistingExamInOrder() {
        JdbcImportDao imports = new JdbcImportDao(database);
        JdbcQuestionDao questions = new JdbcQuestionDao(database);

        long examId = imports.importQuestions(null, new NewExam("匯入", subjectId, 2024, null, userId),
                List.of(question("一"), question("二")));
        imports.importQuestions(examId, null, List.of(question("三")));

        assertEquals(List.of("一", "二", "三"), questions.findByExam(examId).stream().map(QuestionRecord::content).toList());
        assertEquals(List.of(true, false), questions.findByExam(examId).get(0).options().stream().map(o -> o.correct()).toList());
    }

    @Test
    void importsGroupsByIndex() throws Exception {
        JdbcImportDao imports = new JdbcImportDao(database);
        JdbcQuestionDao questions = new JdbcQuestionDao(database);

        long examId = imports.importQuestions(null, new NewExam("題組匯入", subjectId, null, null, userId),
                List.of(question("獨立"), question("甲 1"), question("甲 2"), question("乙 1")),
                List.of("文章甲", "文章乙"), java.util.Arrays.asList(null, 0, 0, 1));

        List<QuestionRecord> loaded = questions.findByExam(examId);
        assertEquals(java.util.Arrays.asList(null, "文章甲", "文章甲", "文章乙"),
                loaded.stream().map(QuestionRecord::passage).toList());
        assertEquals(loaded.get(1).groupId(), loaded.get(2).groupId());
        assertEquals(2, count("SELECT COUNT(*) FROM question_groups WHERE created_by = ?"));
    }

    @Test
    void failureRollsBackEverythingIncludingTheNewExam() throws Exception {
        JdbcImportDao imports = new JdbcImportDao(database);
        NewQuestion broken = new NewQuestion(QuestionType.SINGLE_CHOICE, null, null, null, userId, List.of()); // content NOT NULL

        assertThrows(DataAccessException.class, () -> imports.importQuestions(null,
                new NewExam("不應存在", subjectId, null, null, userId), List.of(question("好的"), broken)));

        assertEquals(0, count("SELECT COUNT(*) FROM exams WHERE created_by = ?"));
        assertEquals(0, count("SELECT COUNT(*) FROM questions WHERE created_by = ?"));

        assertThrows(DataAccessException.class, () -> imports.importQuestions(null,
                new NewExam("不應存在", subjectId, null, null, userId), List.of(question("好的"), broken),
                List.of("文章"), java.util.Arrays.asList(0, 0)));
        assertEquals(0, count("SELECT COUNT(*) FROM question_groups WHERE created_by = ?"), "題組也回滾");
    }

    private int count(String sql) throws Exception {
        try (Connection c = database.connection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
