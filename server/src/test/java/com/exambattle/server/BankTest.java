package com.exambattle.server;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.bank.CreateSubjectRequest;
import com.exambattle.common.protocol.bank.ExamDetail;
import com.exambattle.common.protocol.bank.ExamList;
import com.exambattle.common.protocol.bank.ExamRef;
import com.exambattle.common.protocol.bank.ExamRequest;
import com.exambattle.common.protocol.bank.ExamSummary;
import com.exambattle.common.protocol.bank.ListExamsRequest;
import com.exambattle.common.protocol.bank.OptionData;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionRef;
import com.exambattle.common.protocol.bank.QuestionRequest;
import com.exambattle.common.protocol.bank.QuestionSaved;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.bank.ReorderRequest;
import com.exambattle.common.protocol.bank.SubjectInfo;
import com.exambattle.common.protocol.bank.SubjectList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第三階段題庫：分類、考卷、題目、答案規則與權限（經由協定測試）。 */
class BankTest extends ServerTestBase {

    private TestClient owner;
    private TestClient other;
    private long math;

    @BeforeEach
    void loginTwoUsers() throws Exception {
        owner = loggedIn("teacher");
        other = loggedIn("student");
        math = subjects(owner).stream().filter(s -> s.name().equals("數學")).findFirst().orElseThrow().id();
    }

    // ---------- 分類

    @Test
    void seedSubjectsAreListedAndUsersCanAddMore() throws Exception {
        assertEquals(8, subjects(owner).size());

        SubjectInfo created = owner.request(MessageType.CREATE_SUBJECT, new CreateSubjectRequest("  音樂 "),
                MessageType.SUBJECT_SAVED).payloadAs(SubjectInfo.class);

        assertEquals("音樂", created.name());
        assertTrue(subjects(other).contains(created), "分類全站共用");
        other.expectError(MessageType.CREATE_SUBJECT, new CreateSubjectRequest("音樂"), ErrorCode.SUBJECT_EXISTS);
        other.expectError(MessageType.CREATE_SUBJECT, new CreateSubjectRequest(" "), ErrorCode.INVALID_PAYLOAD);
    }

    // ---------- 考卷

    @Test
    void createExamAndListBySubject() throws Exception {
        ExamSummary exam = createExam(owner, "113 學年度期中考", math, 2024, "期中考");
        long english = subjects(owner).stream().filter(s -> s.name().equals("英文")).findFirst().orElseThrow().id();
        createExam(owner, "多益模擬", english, null, null);

        assertEquals("113 學年度期中考", exam.title());
        assertEquals("數學", exam.subject().name());
        assertEquals(2024, exam.year());
        assertEquals("teacher", exam.owner().username());
        assertEquals(0, exam.questionCount());

        assertEquals(2, listExams(other, null).size(), "所有登入者都能瀏覽全部考卷");
        assertEquals(List.of("113 學年度期中考"), listExams(other, math).stream().map(ExamSummary::title).toList());
    }

    @Test
    void invalidExamFieldsAreRejected() throws Exception {
        owner.expectError(MessageType.CREATE_EXAM, new ExamRequest(null, " ", math, null, null), ErrorCode.INVALID_PAYLOAD);
        owner.expectError(MessageType.CREATE_EXAM, new ExamRequest(null, "x", null, null, null), ErrorCode.INVALID_PAYLOAD);
        owner.expectError(MessageType.CREATE_EXAM, new ExamRequest(null, "x", math, 1800, null), ErrorCode.INVALID_PAYLOAD);
        owner.expectError(MessageType.CREATE_EXAM, new ExamRequest(null, "x".repeat(201), math, null, null),
                ErrorCode.INVALID_PAYLOAD);
        owner.expectError(MessageType.CREATE_EXAM, new ExamRequest(null, "x", 9999L, null, null),
                ErrorCode.SUBJECT_NOT_FOUND);
        owner.expectError(MessageType.GET_EXAM, new ExamRef(9999L), ErrorCode.EXAM_NOT_FOUND);
        owner.expectError(MessageType.GET_EXAM, new ExamRef(null), ErrorCode.INVALID_PAYLOAD);
    }

    @Test
    void ownerCanUpdateAndDeleteExam() throws Exception {
        ExamSummary exam = createExam(owner, "舊名稱", math, null, null);

        ExamSummary updated = owner.request(MessageType.UPDATE_EXAM,
                new ExamRequest(exam.id(), "新名稱", math, 2023, "段考"), MessageType.EXAM_SAVED)
                .payloadAs(ExamSummary.class);
        assertEquals("新名稱", updated.title());
        assertEquals("段考", updated.examType());

        owner.request(MessageType.DELETE_EXAM, new ExamRef(exam.id()), MessageType.EXAM_DELETED);
        owner.expectError(MessageType.GET_EXAM, new ExamRef(exam.id()), ErrorCode.EXAM_NOT_FOUND);
        assertTrue(listExams(owner, null).isEmpty());
    }

    // ---------- 題目與答案規則

    @Test
    void addThreeQuestionTypesWithAnswers() throws Exception {
        long examId = createExam(owner, "綜合測驗", math, null, null).id();

        QuestionData single = addQuestion(owner, examId, choice(QuestionType.SINGLE_CHOICE, "1 + 1 = ?", "1", "2*", "3"));
        QuestionData multi = addQuestion(owner, examId, choice(QuestionType.MULTIPLE_CHOICE, "哪些是質數？", "2*", "3*", "4"));
        QuestionData tf = addQuestion(owner, examId, trueFalse("0 是偶數", true));

        assertTrue(single.answerSet());
        assertEquals(List.of(false, true, false), single.options().stream().map(OptionData::correct).toList());
        assertEquals(2, multi.options().stream().filter(o -> Boolean.TRUE.equals(o.correct())).count());
        assertEquals(Boolean.TRUE, tf.trueFalseAnswer());
        assertTrue(tf.options().isEmpty());

        ExamDetail detail = getExam(owner, examId);
        assertEquals(List.of("1 + 1 = ?", "哪些是質數？", "0 是偶數"),
                detail.questions().stream().map(QuestionData::content).toList());
        assertEquals(3, detail.exam().questionCount());
        assertEquals(0, detail.exam().unansweredCount());
    }

    @Test
    void questionsWithoutAnswerAreAllowedButCounted() throws Exception {
        long examId = createExam(owner, "答案待確認", math, null, null).id();

        QuestionData noAnswer = addQuestion(owner, examId, choice(QuestionType.SINGLE_CHOICE, "答案不確定", "A", "B"));
        addQuestion(owner, examId, trueFalse("是非題未設定", null));

        assertFalse(noAnswer.answerSet());
        assertEquals(2, getExam(owner, examId).exam().unansweredCount());
        assertEquals(2, listExams(owner, math).get(0).unansweredCount());
    }

    @Test
    void answerRulesAreEnforced() throws Exception {
        long examId = createExam(owner, "規則", math, null, null).id();

        expectInvalid(examId, choice(QuestionType.SINGLE_CHOICE, "兩個正確", "A*", "B*"));
        expectInvalid(examId, choice(QuestionType.SINGLE_CHOICE, "只有一個選項", "A*"));
        expectInvalid(examId, choice(QuestionType.MULTIPLE_CHOICE, "空白選項", "A*", "  "));
        expectInvalid(examId, choice(QuestionType.MULTIPLE_CHOICE, "太多選項",
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"));
        expectInvalid(examId, new QuestionData(null, QuestionType.TRUE_FALSE, "是非題有選項",
                null, List.of(new OptionData(null, "A", true)), true, false));
        expectInvalid(examId, new QuestionData(null, null, "沒有題型", null, List.of(), null, false));
        expectInvalid(examId, trueFalse("   ", true));
        assertEquals(0, getExam(owner, examId).questions().size());
    }

    @Test
    void updateQuestionReplacesOptions() throws Exception {
        long examId = createExam(owner, "更新", math, null, null).id();
        QuestionData q = addQuestion(owner, examId, choice(QuestionType.SINGLE_CHOICE, "舊題目", "A*", "B"));

        QuestionData updated = owner.request(MessageType.UPDATE_QUESTION,
                new QuestionRequest(examId, q.id(), choice(QuestionType.MULTIPLE_CHOICE, "新題目", "X*", "Y*", "Z")),
                MessageType.QUESTION_SAVED).payloadAs(QuestionSaved.class).question();

        assertEquals(q.id(), updated.id());
        assertEquals(QuestionType.MULTIPLE_CHOICE, updated.type());
        assertEquals(List.of("X", "Y", "Z"), updated.options().stream().map(OptionData::content).toList());
    }

    @Test
    void deleteAndReorderQuestions() throws Exception {
        long examId = createExam(owner, "排序", math, null, null).id();
        long a = addQuestion(owner, examId, trueFalse("A", true)).id();
        long b = addQuestion(owner, examId, trueFalse("B", true)).id();
        long c = addQuestion(owner, examId, trueFalse("C", true)).id();

        ExamDetail reordered = owner.request(MessageType.REORDER_QUESTIONS, new ReorderRequest(examId, List.of(c, a, b)),
                MessageType.EXAM_DETAIL).payloadAs(ExamDetail.class);
        assertEquals(List.of("C", "A", "B"), reordered.questions().stream().map(QuestionData::content).toList());

        owner.expectError(MessageType.REORDER_QUESTIONS, new ReorderRequest(examId, List.of(c, a)), ErrorCode.INVALID_PAYLOAD);
        owner.expectError(MessageType.REORDER_QUESTIONS, new ReorderRequest(examId, List.of(c, a, a)),
                ErrorCode.INVALID_PAYLOAD);

        owner.request(MessageType.DELETE_QUESTION, new QuestionRef(examId, a), MessageType.QUESTION_DELETED);
        assertEquals(List.of("C", "B"), getExam(owner, examId).questions().stream().map(QuestionData::content).toList());
        owner.expectError(MessageType.DELETE_QUESTION, new QuestionRef(examId, a), ErrorCode.QUESTION_NOT_FOUND);
    }

    @Test
    void questionMustBelongToExam() throws Exception {
        long exam1 = createExam(owner, "一", math, null, null).id();
        long exam2 = createExam(owner, "二", math, null, null).id();
        long q = addQuestion(owner, exam1, trueFalse("屬於一", true)).id();

        owner.expectError(MessageType.UPDATE_QUESTION, new QuestionRequest(exam2, q, trueFalse("改", false)),
                ErrorCode.QUESTION_NOT_FOUND);
        owner.expectError(MessageType.DELETE_QUESTION, new QuestionRef(exam2, q), ErrorCode.QUESTION_NOT_FOUND);
    }

    // ---------- 權限與答案保護

    @Test
    void othersCanViewButNotSeeAnswers() throws Exception {
        long examId = createExam(owner, "保密", math, null, null).id();
        addQuestion(owner, examId, choice(QuestionType.SINGLE_CHOICE, "選擇", "A", "B*"));
        addQuestion(owner, examId, trueFalse("是非", false));

        ExamDetail seen = getExam(other, examId);

        assertEquals(2, seen.questions().size());
        for (QuestionData q : seen.questions()) {
            assertTrue(q.answerSet(), "仍可得知是否已設定答案");
            assertNull(q.trueFalseAnswer());
            q.options().forEach(o -> assertNull(o.correct(), "非建立者不可看到正確答案"));
        }
        assertEquals(List.of("A", "B"), seen.questions().get(0).options().stream().map(OptionData::content).toList());
    }

    @Test
    void onlyOwnerCanModify() throws Exception {
        long examId = createExam(owner, "唯讀", math, null, null).id();
        long q = addQuestion(owner, examId, trueFalse("Q", true)).id();

        other.expectError(MessageType.UPDATE_EXAM, new ExamRequest(examId, "偷改", math, null, null), ErrorCode.NOT_OWNER);
        other.expectError(MessageType.DELETE_EXAM, new ExamRef(examId), ErrorCode.NOT_OWNER);
        other.expectError(MessageType.ADD_QUESTION, new QuestionRequest(examId, null, trueFalse("X", true)),
                ErrorCode.NOT_OWNER);
        other.expectError(MessageType.UPDATE_QUESTION, new QuestionRequest(examId, q, trueFalse("X", true)),
                ErrorCode.NOT_OWNER);
        other.expectError(MessageType.DELETE_QUESTION, new QuestionRef(examId, q), ErrorCode.NOT_OWNER);
        other.expectError(MessageType.REORDER_QUESTIONS, new ReorderRequest(examId, List.of(q)), ErrorCode.NOT_OWNER);
        assertEquals("唯讀", getExam(owner, examId).exam().title());
    }

    @Test
    void bankRequiresLogin() throws Exception {
        TestClient anonymous = connect();
        anonymous.hello();

        anonymous.expectError(MessageType.LIST_SUBJECTS, null, ErrorCode.NOT_LOGGED_IN);
        anonymous.expectError(MessageType.CREATE_EXAM, new ExamRequest(null, "x", math, null, null),
                ErrorCode.NOT_LOGGED_IN);
    }

    // ---------- 輔助方法

    private static List<SubjectInfo> subjects(TestClient c) throws Exception {
        return c.request(MessageType.LIST_SUBJECTS, null, MessageType.SUBJECTS).payloadAs(SubjectList.class).subjects();
    }

    private static ExamSummary createExam(TestClient c, String title, long subjectId, Integer year, String type)
            throws Exception {
        return c.request(MessageType.CREATE_EXAM, new ExamRequest(null, title, subjectId, year, type, com.exambattle.common.protocol.bank.Visibility.PUBLIC),
                MessageType.EXAM_SAVED).payloadAs(ExamSummary.class);
    }

    private static List<ExamSummary> listExams(TestClient c, Long subjectId) throws Exception {
        return c.request(MessageType.LIST_EXAMS, new ListExamsRequest(subjectId), MessageType.EXAMS)
                .payloadAs(ExamList.class).exams();
    }

    private static ExamDetail getExam(TestClient c, long examId) throws Exception {
        return c.request(MessageType.GET_EXAM, new ExamRef(examId), MessageType.EXAM_DETAIL).payloadAs(ExamDetail.class);
    }

    private static QuestionData addQuestion(TestClient c, long examId, QuestionData q) throws Exception {
        return c.request(MessageType.ADD_QUESTION, new QuestionRequest(examId, null, q), MessageType.QUESTION_SAVED)
                .payloadAs(QuestionSaved.class).question();
    }

    private void expectInvalid(long examId, QuestionData q) throws Exception {
        owner.expectError(MessageType.ADD_QUESTION, new QuestionRequest(examId, null, q), ErrorCode.INVALID_PAYLOAD);
    }

    /** 選項以 * 結尾表示正確答案。 */
    private static QuestionData choice(QuestionType type, String content, String... options) {
        List<OptionData> list = new ArrayList<>();
        for (String o : options) {
            boolean correct = o.endsWith("*");
            list.add(new OptionData(null, correct ? o.substring(0, o.length() - 1) : o, correct));
        }
        return new QuestionData(null, type, content, "解說", list, null, false);
    }

    private static QuestionData trueFalse(String content, Boolean answer) {
        return new QuestionData(null, QuestionType.TRUE_FALSE, content, null, List.of(), answer, false);
    }
}
