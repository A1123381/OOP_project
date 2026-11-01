package com.exambattle.server.dao;

import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.practice.PracticeMode;
import com.exambattle.common.protocol.practice.RevealMode;
import com.exambattle.server.dao.BankRecords.NewExam;
import com.exambattle.server.dao.BankRecords.NewOption;
import com.exambattle.server.dao.BankRecords.NewQuestion;
import com.exambattle.server.dao.PracticeRecords.NewAnswer;
import com.exambattle.server.dao.PracticeRecords.NewRecord;
import com.exambattle.server.dao.PracticeRecords.RecordDetail;
import com.exambattle.server.dao.PracticeRecords.StatsRow;
import com.exambattle.server.dao.PracticeRecords.WrongRow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 對真正的 MariaDB 測試練習紀錄與錯題本（交易、快照、統計、錯題累計、刪除題目後的行為）。
 * 只有設定 EXAMBATTLE_TEST_DB_URL 時才執行；只刪除本測試建立的資料。
 */
@EnabledIfEnvironmentVariable(named = "EXAMBATTLE_TEST_DB_URL", matches = ".+")
class JdbcPracticeDaoTest {

    private static final Instant T0 = Instant.parse("2026-11-01T04:00:00.123Z");

    private Database database;
    private JdbcPracticeDao practice;
    private JdbcQuestionDao questions;
    private long userId;
    private long subjectId;
    private long examId;
    private long q1;
    private long q2;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(new DbConfig(System.getenv("EXAMBATTLE_TEST_DB_URL"),
                System.getenv("EXAMBATTLE_TEST_DB_USER"),
                System.getenv().getOrDefault("EXAMBATTLE_TEST_DB_PASSWORD", "")));
        database.verify();
        practice = new JdbcPracticeDao(database);
        questions = new JdbcQuestionDao(database);
        String prefix = "t_" + UUID.randomUUID().toString().substring(0, 8) + "_";
        userId = new JdbcUserDao(database).create(new NewUser(prefix + "u", "Tester", new byte[32], new byte[16], 1)).id();
        subjectId = new JdbcSubjectDao(database).create(prefix + "科目", userId).id();
        examId = new JdbcExamDao(database).create(new NewExam("練習用考卷", subjectId, null, null, userId)).id();
        q1 = questions.createInExam(examId, new NewQuestion(QuestionType.SINGLE_CHOICE, "單選", null, null, userId,
                List.of(new NewOption("A", true), new NewOption("B", false)))).id();
        q2 = questions.createInExam(examId, new NewQuestion(QuestionType.TRUE_FALSE, "是非", null, null, userId,
                List.of())).id();
    }

    @AfterEach
    void cleanUp() throws Exception {
        try (Connection c = database.connection()) {
            for (String sql : List.of(
                    "DELETE FROM practice_records WHERE user_id = ?",
                    "DELETE FROM wrong_questions WHERE user_id = ?",
                    "DELETE FROM exams WHERE created_by = ?",
                    "DELETE FROM questions WHERE created_by = ?",
                    "DELETE FROM question_groups WHERE created_by = ?",
                    "DELETE FROM subjects WHERE created_by = ?",
                    "DELETE FROM users WHERE id = ?")) {
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setLong(1, userId);
                    ps.executeUpdate();
                }
            }
        }
    }

    private NewRecord record(Instant start, double score, List<NewAnswer> answers) {
        return new NewRecord(userId, PracticeMode.FULL_EXAM, RevealMode.IMMEDIATE, "練習用考卷", examId, null,
                answers.size(), (int) answers.stream().filter(NewAnswer::correct).count(), score,
                start, start.plusMillis(65_432), answers);
    }

    @Test
    void savesRecordWithAnswersAndReadsBackExactly() {
        long id = practice.save(record(T0, 50.0, List.of(
                new NewAnswer(0, q1, "{\"content\":\"單選\"}", "{\"selectedOptionIds\":[1]}", 1.0, true, T0.plusSeconds(5)),
                new NewAnswer(1, q2, "{\"content\":\"是非\"}", "{\"trueFalseAnswer\":false}", 0.0, false, null))),
                List.of(q2));

        RecordDetail detail = practice.find(id).orElseThrow();
        assertEquals(T0, detail.record().startedAt(), "DATETIME(3) 以 UTC 往返不失真");
        assertEquals(65_432, detail.record().durationMs());
        assertEquals(50.0, detail.record().score());
        assertEquals(2, detail.answers().size());
        assertEquals("{\"content\":\"單選\"}", detail.answers().get(0).snapshotJson());
        assertEquals(T0.plusSeconds(5), detail.answers().get(0).answeredAt());
        assertNull(detail.answers().get(1).answeredAt());
        assertEquals(List.of(id), practice.list(userId).stream().map(r -> r.id()).toList());
    }

    @Test
    void statsAggregateAllRecords() {
        practice.save(record(T0, 100.0, List.of(
                new NewAnswer(0, q1, "{}", "{}", 1.0, true, null))), List.of());
        practice.save(record(T0.plusSeconds(100), 0.0, List.of(
                new NewAnswer(0, q1, "{}", "{}", 0.0, false, null),
                new NewAnswer(1, q2, "{}", "{}", 0.0, false, null))), List.of(q1, q2));

        StatsRow stats = practice.stats(userId);
        assertEquals(2, stats.practiceCount());
        assertEquals(3, stats.questionCount());
        assertEquals(1, stats.correctCount());
        assertEquals(50.0, stats.averageScore(), 1e-9);
        assertEquals(T0.plusSeconds(100).plusMillis(65_432), practice.list(userId).get(0).finishedAt(), "新到舊");
    }

    @Test
    void wrongBookUpsertsCountsAndShowsExamAndSubject() {
        practice.save(record(T0, 0.0, List.of(new NewAnswer(0, q1, "{}", "{}", 0.0, false, null))), List.of(q1));
        practice.save(record(T0.plusSeconds(60), 0.0, List.of(new NewAnswer(0, q1, "{}", "{}", 0.0, false, null))), List.of(q1, q2));

        List<WrongRow> rows = practice.list(userId, null);
        WrongRow first = rows.stream().filter(r -> r.questionId() == q1).findFirst().orElseThrow();
        assertEquals(2, first.wrongCount());
        assertEquals("練習用考卷", first.examTitle());
        assertEquals(subjectId, first.subjectId());
        assertEquals(2, practice.list(userId, subjectId).size());
        assertTrue(practice.list(userId, subjectId + 100_000).isEmpty());

        assertTrue(practice.remove(userId, q1));
        assertTrue(!practice.remove(userId, q1));
        assertEquals(List.of(q2), practice.list(userId, null).stream().map(WrongRow::questionId).toList());
    }

    @Test
    void deletedQuestionKeepsSnapshotAndLeavesWrongBook() {
        long id = practice.save(record(T0, 0.0, List.of(new NewAnswer(0, q1, "{\"content\":\"單選\"}", "{}", 0.0, false, null))),
                List.of(q1));

        questions.removeFromExam(examId, q1); // 題目不再屬於任何考卷 → 被刪除

        RecordDetail detail = practice.find(id).orElseThrow();
        assertNull(detail.answers().get(0).questionId(), "ON DELETE SET NULL");
        assertEquals("{\"content\":\"單選\"}", detail.answers().get(0).snapshotJson());
        assertTrue(practice.list(userId, null).isEmpty(), "ON DELETE CASCADE");

        // 練習進行中題目被刪除：交卷仍可寫入，不違反外鍵
        long late = practice.save(record(T0, 0.0, List.of(new NewAnswer(0, q1, "{}", "{}", 0.0, false, null))), List.of(q1));
        assertNull(practice.find(late).orElseThrow().answers().get(0).questionId());
        assertTrue(practice.list(userId, null).isEmpty());
    }

    @Test
    void answeredIdsOnlyIncludeQuestionsWithAnswers() {
        assertEquals(List.of(q1), questions.answeredIds(examId, null), "是非題未設答案不列入");
        assertEquals(List.of(q1), questions.answeredIds(null, subjectId));
        assertTrue(questions.answeredIds(null, null).contains(q1));
    }
}
