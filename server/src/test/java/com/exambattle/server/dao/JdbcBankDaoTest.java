package com.exambattle.server.dao;

import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.server.dao.BankRecords.ExamRecord;
import com.exambattle.server.dao.BankRecords.ExamStats;
import com.exambattle.server.dao.BankRecords.NewExam;
import com.exambattle.server.dao.BankRecords.NewOption;
import com.exambattle.server.dao.BankRecords.NewQuestion;
import com.exambattle.server.dao.BankRecords.QuestionRecord;
import com.exambattle.server.dao.BankRecords.SubjectRecord;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 對真正的 MariaDB 測試題庫 DAO（交易、CASCADE、孤兒題目清除、統計）。
 * 只有設定 EXAMBATTLE_TEST_DB_URL 時才執行；只刪除本測試建立的資料。
 */
@EnabledIfEnvironmentVariable(named = "EXAMBATTLE_TEST_DB_URL", matches = ".+")
class JdbcBankDaoTest {

    private Database database;
    private JdbcSubjectDao subjects;
    private JdbcExamDao exams;
    private JdbcQuestionDao questions;
    private String prefix;
    private long userId;
    private long subjectId;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database(new DbConfig(System.getenv("EXAMBATTLE_TEST_DB_URL"),
                System.getenv("EXAMBATTLE_TEST_DB_USER"),
                System.getenv().getOrDefault("EXAMBATTLE_TEST_DB_PASSWORD", "")));
        database.verify();
        subjects = new JdbcSubjectDao(database);
        exams = new JdbcExamDao(database);
        questions = new JdbcQuestionDao(database);
        prefix = "t_" + UUID.randomUUID().toString().substring(0, 8) + "_";
        userId = new JdbcUserDao(database)
                .create(new NewUser(prefix + "u", "Tester", new byte[32], new byte[16], 1)).id();
        subjectId = subjects.create(prefix + "科目", userId).id();
    }

    @AfterEach
    void cleanUp() throws Exception {
        try (Connection c = database.connection()) {
            for (String sql : List.of(
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

    @Test
    void subjectNamesAreUniqueIgnoringCase() throws Exception {
        subjects.create(prefix + "Music", userId);

        assertThrows(SubjectExistsException.class, () -> subjects.create((prefix + "MUSIC").toUpperCase(), userId));
        assertTrue(subjects.list().stream().map(SubjectRecord::name).anyMatch(n -> n.equals(prefix + "Music")));
    }

    @Test
    void examCrudWithStats() {
        ExamRecord exam = exams.create(new NewExam("期中考", subjectId, null, null, userId));
        questions.createInExam(exam.id(), trueFalse("有答案", true));
        questions.createInExam(exam.id(), trueFalse("沒答案", null));
        questions.createInExam(exam.id(), choice(QuestionType.SINGLE_CHOICE, "沒答案的選擇題", false, false));

        ExamStats stats = exams.findById(exam.id()).orElseThrow();
        assertEquals(3, stats.questionCount());
        assertEquals(2, stats.unansweredCount());
        assertNull(stats.exam().year());

        exams.update(new ExamRecord(exam.id(), "改名", subjectId, 2024, "段考", userId));
        ExamStats updated = exams.list(subjectId).get(0);
        assertEquals("改名", updated.exam().title());
        assertEquals(2024, updated.exam().year());
    }

    @Test
    void listFiltersVisibilityAndSearchesOwner() {
        long mine = exams.create(new NewExam(prefix + "公開卷", subjectId, 2025, "模擬考", userId,
                com.exambattle.common.protocol.bank.Visibility.PUBLIC)).id();
        long hidden = exams.create(new NewExam(prefix + "私人卷", subjectId, null, null, userId,
                com.exambattle.common.protocol.bank.Visibility.PRIVATE)).id();
        long stranger = -1L;

        List<Long> seenByOthers = exams.list(new BankRecords.ExamQuery(subjectId, null, stranger, false, false)).stream()
                .map(s -> s.exam().id()).toList();
        assertTrue(seenByOthers.contains(mine));
        assertFalse(seenByOthers.contains(hidden), "別人看不到私人考卷");
        assertTrue(exams.list(new BankRecords.ExamQuery(subjectId, null, userId, false, true)).stream()
                .anyMatch(s -> s.exam().id() == hidden), "自己看得到");

        // 以建立者帳號搜尋（只在 JDBC 實作中比對 users）
        List<Long> byOwner = exams.list(new BankRecords.ExamQuery(null, prefix, stranger, false, false)).stream()
                .map(s -> s.exam().id()).toList();
        assertTrue(byOwner.contains(mine));

        ExamRecord down = exams.findById(mine).orElseThrow().exam();
        exams.update(new ExamRecord(down.id(), down.title(), down.subjectId(), down.year(), down.examType(), userId,
                com.exambattle.common.protocol.bank.Visibility.PRIVATE, true));
        assertTrue(exams.findById(mine).orElseThrow().exam().takenDown());
        assertTrue(exams.list(new BankRecords.ExamQuery(subjectId, null, stranger, true, false)).stream()
                .anyMatch(s -> s.exam().id() == mine), "管理員看得到被下架的考卷");
    }

    @Test
    void questionsKeepOrderOptionsAndAnswers() {
        long examId = exams.create(new NewExam("順序", subjectId, 2023, "模擬考", userId)).id();
        QuestionRecord first = questions.createInExam(examId, choice(QuestionType.MULTIPLE_CHOICE, "多選", true, false, true));
        QuestionRecord second = questions.createInExam(examId, trueFalse("是非", false));

        List<QuestionRecord> loaded = questions.findByExam(examId);
        assertEquals(List.of(first.id(), second.id()), loaded.stream().map(QuestionRecord::id).toList());
        assertEquals(List.of(true, false, true), loaded.get(0).options().stream().map(o -> o.correct()).toList());
        assertEquals(Boolean.FALSE, loaded.get(1).trueFalseAnswer());

        questions.reorder(examId, List.of(second.id(), first.id()));
        assertEquals(List.of(second.id(), first.id()), questions.questionIdsOf(examId));

        QuestionRecord updated = questions.update(first.id(), choice(QuestionType.SINGLE_CHOICE, "改單選", false, true));
        assertEquals(2, updated.options().size());
        assertEquals("改單選", questions.findById(first.id()).orElseThrow().content());
    }

    @Test
    void removingAndDeletingCleansUpOrphanQuestions() throws Exception {
        long examId = exams.create(new NewExam("刪除", subjectId, null, null, userId)).id();
        long q1 = questions.createInExam(examId, choice(QuestionType.SINGLE_CHOICE, "一", true, false)).id();
        long q2 = questions.createInExam(examId, trueFalse("二", true)).id();

        questions.removeFromExam(examId, q1);
        assertTrue(questions.findById(q1).isEmpty(), "不屬於任何考卷的題目應被刪除");
        assertEquals(0, countOptions(q1), "選項應隨題目刪除（CASCADE）");
        assertFalse(questions.isInExam(examId, q1));

        exams.delete(examId);
        assertTrue(exams.findById(examId).isEmpty());
        assertTrue(questions.findById(q2).isEmpty());
    }

    @Test
    void groupsShareOnePassageAndDisappearWhenEmpty() throws Exception {
        long examId = exams.create(new NewExam("題組", subjectId, null, null, userId)).id();
        QuestionRecord first = questions.createInExam(examId,
                choice(QuestionType.SINGLE_CHOICE, "第 1 格", true, false).withGroup(null, "文章【1】【2】"));
        long groupId = first.groupId();
        QuestionRecord second = questions.createInExam(examId,
                choice(QuestionType.SINGLE_CHOICE, "第 2 格", false, true).withGroup(groupId, null));
        QuestionRecord alone = questions.createInExam(examId, trueFalse("獨立", true));

        assertEquals("文章【1】【2】", first.passage());
        assertEquals(groupId, second.groupId());
        assertNull(alone.groupId());
        assertTrue(questions.isGroupInExam(examId, groupId));
        assertEquals(1, countGroups(groupId));

        questions.updatePassage(groupId, "改過的文章");
        assertEquals(List.of("改過的文章", "改過的文章"), questions.findByExam(examId).stream().limit(2)
                .map(QuestionRecord::passage).toList(), "文章只存一份");

        questions.update(first.id(), choice(QuestionType.SINGLE_CHOICE, "移出題組", true, false));
        assertNull(questions.findById(first.id()).orElseThrow().groupId());
        assertEquals(1, countGroups(groupId), "還有第 2 題，題組保留");

        questions.removeFromExam(examId, second.id());
        assertEquals(0, countGroups(groupId), "題組沒有題目後一併刪除");
        assertFalse(questions.isGroupInExam(examId, groupId));

        long other = questions.createInExam(examId,
                choice(QuestionType.SINGLE_CHOICE, "新題組", true, false).withGroup(null, "另一篇")).groupId();
        exams.delete(examId);
        assertEquals(0, countGroups(other), "刪除考卷時清除題組");
    }

    private int countGroups(long groupId) throws Exception {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM question_groups WHERE id = ?")) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int countOptions(long questionId) throws Exception {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM question_options WHERE question_id = ?")) {
            ps.setLong(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private NewQuestion trueFalse(String content, Boolean answer) {
        return new NewQuestion(QuestionType.TRUE_FALSE, content, null, answer, userId, List.of());
    }

    private NewQuestion choice(QuestionType type, String content, boolean... correct) {
        List<NewOption> options = new java.util.ArrayList<>();
        for (int i = 0; i < correct.length; i++) {
            options.add(new NewOption("選項" + (i + 1), correct[i]));
        }
        return new NewQuestion(type, content, "解說", null, userId, options);
    }
}
