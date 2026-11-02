package com.exambattle.server;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.bank.ExamDetail;
import com.exambattle.common.protocol.bank.ExamList;
import com.exambattle.common.protocol.bank.ExamRef;
import com.exambattle.common.protocol.bank.ExamRequest;
import com.exambattle.common.protocol.bank.ExamSummary;
import com.exambattle.common.protocol.bank.ImportDone;
import com.exambattle.common.protocol.bank.ImportRequest;
import com.exambattle.common.protocol.bank.ListExamsRequest;
import com.exambattle.common.protocol.bank.OptionData;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionRequest;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.bank.SubjectList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第七階段：整批匯入題目（IMPORT_QUESTIONS）。 */
class ImportTest extends ServerTestBase {

    private TestClient owner;
    private TestClient other;
    private long math;

    @BeforeEach
    void login() throws Exception {
        owner = loggedIn("teacher");
        other = loggedIn("student");
        math = owner.request(MessageType.LIST_SUBJECTS, null, MessageType.SUBJECTS).payloadAs(SubjectList.class)
                .subjects().stream().filter(s -> s.name().equals("數學")).findFirst().orElseThrow().id();
    }

    @Test
    void importIntoNewExamKeepsOrderAndAnswers() throws Exception {
        ImportDone done = importQuestions(owner, new ImportRequest(null,
                new ExamRequest(null, " 匯入的考卷 ", math, 2024, "期中考"),
                List.of(single("第一題", 1), multi("第二題"), trueFalse("第三題", null))));

        assertEquals(3, done.importedCount());
        assertEquals("匯入的考卷", done.exam().title());
        assertEquals(3, done.exam().questionCount());
        assertEquals(1, done.exam().unansweredCount(), "答案未設定的題目可以匯入");
        ExamDetail detail = owner.request(MessageType.GET_EXAM, new ExamRef(done.exam().id()), MessageType.EXAM_DETAIL)
                .payloadAs(ExamDetail.class);
        assertEquals(List.of("第一題", "第二題", "第三題"), detail.questions().stream().map(QuestionData::content).toList());
        assertEquals(List.of(false, true), detail.questions().get(0).options().stream().map(OptionData::correct).toList());
    }

    @Test
    void importAppendsToOwnExistingExam() throws Exception {
        long examId = owner.request(MessageType.CREATE_EXAM, new ExamRequest(null, "既有考卷", math, null, null),
                MessageType.EXAM_SAVED).payloadAs(ExamSummary.class).id();
        owner.request(MessageType.ADD_QUESTION, new QuestionRequest(examId, null, single("原本的題目", 0)),
                MessageType.QUESTION_SAVED);

        ImportDone done = importQuestions(owner, new ImportRequest(examId, null, List.of(single("匯入一", 0), single("匯入二", 1))));

        assertEquals(2, done.importedCount());
        assertEquals(3, done.exam().questionCount());
        List<String> contents = owner.request(MessageType.GET_EXAM, new ExamRef(examId), MessageType.EXAM_DETAIL)
                .payloadAs(ExamDetail.class).questions().stream().map(QuestionData::content).toList();
        assertEquals(List.of("原本的題目", "匯入一", "匯入二"), contents, "加在既有題目之後");
    }

    @Test
    void onlyOwnerCanImportIntoExistingExam() throws Exception {
        long examId = owner.request(MessageType.CREATE_EXAM, new ExamRequest(null, "老師的考卷", math, null, null, com.exambattle.common.protocol.bank.Visibility.PUBLIC),
                MessageType.EXAM_SAVED).payloadAs(ExamSummary.class).id();

        other.expectError(MessageType.IMPORT_QUESTIONS, new ImportRequest(examId, null, List.of(single("x", 0))),
                ErrorCode.NOT_OWNER);
        other.expectError(MessageType.IMPORT_QUESTIONS, new ImportRequest(9999L, null, List.of(single("x", 0))),
                ErrorCode.EXAM_NOT_FOUND);
    }

    @Test
    void anyInvalidQuestionRejectsTheWholeBatch() throws Exception {
        QuestionData twoCorrectSingle = new QuestionData(null, QuestionType.SINGLE_CHOICE, "兩個正確答案", null,
                List.of(new OptionData(null, "A", true), new OptionData(null, "B", true)), null, false);

        String message = owner.expectError(MessageType.IMPORT_QUESTIONS, new ImportRequest(null,
                new ExamRequest(null, "不應建立", math, null, null),
                List.of(single("好的題目", 0), twoCorrectSingle)), ErrorCode.INVALID_PAYLOAD).message();

        assertTrue(message.startsWith("第 2 題"), message);
        assertTrue(listExams(owner).isEmpty(), "整批拒絕時不會建立考卷");
    }

    @Test
    void requestShapeIsValidated() throws Exception {
        long examId = owner.request(MessageType.CREATE_EXAM, new ExamRequest(null, "考卷", math, null, null),
                MessageType.EXAM_SAVED).payloadAs(ExamSummary.class).id();
        ExamRequest newExam = new ExamRequest(null, "新考卷", math, null, null);

        owner.expectError(MessageType.IMPORT_QUESTIONS, new ImportRequest(examId, newExam, List.of(single("x", 0))),
                ErrorCode.INVALID_PAYLOAD);
        owner.expectError(MessageType.IMPORT_QUESTIONS, new ImportRequest(null, null, List.of(single("x", 0))),
                ErrorCode.INVALID_PAYLOAD);
        owner.expectError(MessageType.IMPORT_QUESTIONS, new ImportRequest(null, newExam, List.of()),
                ErrorCode.INVALID_PAYLOAD);
        owner.expectError(MessageType.IMPORT_QUESTIONS, new ImportRequest(null,
                new ExamRequest(null, "x", 9999L, null, null), List.of(single("x", 0))), ErrorCode.SUBJECT_NOT_FOUND);
        owner.expectError(MessageType.IMPORT_QUESTIONS, new ImportRequest(null, newExam,
                Collections.nCopies(Protocol.MAX_IMPORT_QUESTIONS + 1, single("x", 0))), ErrorCode.TOO_MANY_QUESTIONS);
        assertEquals(1, listExams(owner).size());
    }

    @Test
    void largeImportFitsInOneLine() throws Exception {
        List<QuestionData> many = new ArrayList<>();
        for (int i = 0; i < Protocol.MAX_IMPORT_QUESTIONS; i++) {
            many.add(new QuestionData(null, QuestionType.SINGLE_CHOICE, "第 " + (i + 1) + " 題 " + "很長的題目內容".repeat(30),
                    "解說".repeat(50), List.of(new OptionData(null, "選項甲", true), new OptionData(null, "選項乙", false),
                    new OptionData(null, "選項丙", false), new OptionData(null, "選項丁", false)), null, false));
        }

        ImportDone done = importQuestions(owner, new ImportRequest(null, new ExamRequest(null, "大量匯入", math, null, null), many));

        assertEquals(Protocol.MAX_IMPORT_QUESTIONS, done.importedCount());
        assertFalse(listExams(owner).isEmpty());
    }

    // ---------- 輔助

    private static ImportDone importQuestions(TestClient c, ImportRequest request) throws Exception {
        return c.request(MessageType.IMPORT_QUESTIONS, request, MessageType.IMPORT_DONE).payloadAs(ImportDone.class);
    }

    private static List<ExamSummary> listExams(TestClient c) throws Exception {
        return c.request(MessageType.LIST_EXAMS, new ListExamsRequest(null), MessageType.EXAMS)
                .payloadAs(ExamList.class).exams();
    }

    private static QuestionData single(String content, int correctIndex) {
        return new QuestionData(null, QuestionType.SINGLE_CHOICE, content, null,
                List.of(new OptionData(null, "甲", correctIndex == 0), new OptionData(null, "乙", correctIndex == 1)),
                null, false);
    }

    private static QuestionData multi(String content) {
        return new QuestionData(null, QuestionType.MULTIPLE_CHOICE, content, null,
                List.of(new OptionData(null, "A", true), new OptionData(null, "B", true), new OptionData(null, "C", false)),
                null, false);
    }

    private static QuestionData trueFalse(String content, Boolean answer) {
        return new QuestionData(null, QuestionType.TRUE_FALSE, content, null, List.of(), answer, false);
    }
}
