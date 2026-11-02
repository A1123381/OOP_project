package com.exambattle.server;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageCodec;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.bank.ExamRef;
import com.exambattle.common.protocol.bank.ExamRequest;
import com.exambattle.common.protocol.bank.ExamSummary;
import com.exambattle.common.protocol.bank.OptionData;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionRequest;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.bank.SubjectList;
import com.exambattle.common.protocol.practice.AnswerData;
import com.exambattle.common.protocol.practice.AnswerRequest;
import com.exambattle.common.protocol.practice.AnswerResult;
import com.exambattle.common.protocol.practice.ListWrongRequest;
import com.exambattle.common.protocol.practice.PracticeMode;
import com.exambattle.common.protocol.practice.PracticeQuestion;
import com.exambattle.common.protocol.practice.PracticeResult;
import com.exambattle.common.protocol.practice.PracticeStarted;
import com.exambattle.common.protocol.practice.RecordList;
import com.exambattle.common.protocol.practice.RecordRef;
import com.exambattle.common.protocol.practice.ResultItem;
import com.exambattle.common.protocol.practice.RevealMode;
import com.exambattle.common.protocol.practice.StartPracticeRequest;
import com.exambattle.common.protocol.practice.SubmitRequest;
import com.exambattle.common.protocol.practice.WrongItem;
import com.exambattle.common.protocol.practice.WrongList;
import com.exambattle.common.protocol.practice.WrongRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第五階段：單人練習、紀錄、統計與錯題本（經由協定測試）。 */
class PracticeTest extends ServerTestBase {

    private TestClient owner;
    private TestClient student;
    private long math;
    private long examId;

    @BeforeEach
    void createExam() throws Exception {
        owner = loggedIn("teacher");
        student = loggedIn("student");
        math = owner.request(MessageType.LIST_SUBJECTS, null, MessageType.SUBJECTS).payloadAs(SubjectList.class)
                .subjects().stream().filter(s -> s.name().equals("數學")).findFirst().orElseThrow().id();
        examId = owner.request(MessageType.CREATE_EXAM, new ExamRequest(null, "期中考", math, 2024, null, com.exambattle.common.protocol.bank.Visibility.PUBLIC),
                MessageType.EXAM_SAVED).payloadAs(ExamSummary.class).id();
        add(choice(QuestionType.SINGLE_CHOICE, "單選 1+1", "1", "2*", "3"));
        add(choice(QuestionType.MULTIPLE_CHOICE, "多選 質數", "2*", "3*", "4", "6", "8"));
        add(new QuestionData(null, QuestionType.TRUE_FALSE, "是非 0 是偶數", "0 可被 2 整除", List.of(), true, false));
        add(choice(QuestionType.SINGLE_CHOICE, "答案未設定", "甲", "乙"));
    }

    // ---------- 開始

    @Test
    void startHidesAnswersAndSkipsUnansweredQuestions() throws Exception {
        String id = student.send(MessageType.START_PRACTICE, fullExam(RevealMode.AFTER_SUBMIT));
        Message raw = student.nextOfType(MessageType.PRACTICE_STARTED);
        PracticeStarted started = raw.payloadAs(PracticeStarted.class);

        assertEquals(id, raw.requestId());
        assertEquals(List.of("單選 1+1", "多選 質數", "是非 0 是偶數"),
                started.questions().stream().map(PracticeQuestion::content).toList());
        assertEquals("期中考", started.title());
        String json = MessageCodec.encode(raw);
        assertFalse(json.contains("correct") || json.contains("trueFalseAnswer") || json.contains("explanation"),
                "開始練習時不可傳送答案：" + json);
    }

    // ---------- 考試模式

    @Test
    void examModeGradesOnSubmitWithPartialCreditAndStoresRecord() throws Exception {
        PracticeStarted p = start(student, fullExam(RevealMode.AFTER_SUBMIT));
        List<AnswerData> answers = List.of(
                pick(p, 0, "2"),               // 單選正確
                pick(p, 1, "2"),               // 多選少選 3：k=1 → 0.6
                new AnswerData(2, null, false)); // 是非錯

        PracticeResult result = submit(student, p, answers);

        assertEquals(53.3, result.record().score());
        assertEquals(3, result.record().questionCount());
        assertEquals(1, result.record().correctCount());
        assertEquals(List.of(1.0, 0.6, 0.0), result.questions().stream().map(ResultItem::score).toList());
        ResultItem multi = result.questions().get(1);
        assertFalse(multi.correct(), "部分給分不算答對");
        assertEquals(List.of(true, true, false, false, false),
                multi.question().options().stream().map(OptionData::correct).toList(), "交卷後提供正確答案");
        assertEquals("0 可被 2 整除", result.questions().get(2).question().explanation());

        assertEquals(List.of("多選 質數", "是非 0 是偶數"), wrongContents(student, null));
        RecordList records = student.request(MessageType.LIST_RECORDS, null, MessageType.RECORDS)
                .payloadAs(RecordList.class);
        assertEquals(1, records.stats().practiceCount());
        assertEquals(3, records.stats().questionCount());
        assertEquals(1, records.stats().correctCount());
        assertEquals(53.3, records.stats().averageScore());
    }

    @Test
    void unansweredQuestionsScoreZeroAndBecomeWrong() throws Exception {
        PracticeStarted p = start(student, fullExam(RevealMode.AFTER_SUBMIT));

        PracticeResult result = submit(student, p, List.of(pick(p, 0, "2")));

        assertEquals(33.3, result.record().score());
        assertEquals(List.of(), result.questions().get(1).answer().selectedOptionIds());
        assertEquals(2, wrongContents(student, null).size());
    }

    @Test
    void examModeRejectsPerQuestionAnswers() throws Exception {
        PracticeStarted p = start(student, fullExam(RevealMode.AFTER_SUBMIT));

        student.expectError(MessageType.ANSWER_QUESTION, new AnswerRequest(p.practiceId(), 0, List.of(), null),
                ErrorCode.WRONG_MODE);
    }

    // ---------- 練習模式

    @Test
    void immediateModeRevealsEachAnswerOnce() throws Exception {
        PracticeStarted p = start(student, fullExam(RevealMode.IMMEDIATE));
        long wrongOption = optionId(p, 0, "3");

        AnswerResult first = student.request(MessageType.ANSWER_QUESTION,
                new AnswerRequest(p.practiceId(), 0, List.of(wrongOption), null), MessageType.ANSWER_RESULT)
                .payloadAs(AnswerResult.class);

        assertFalse(first.correct());
        assertEquals(List.of(optionId(p, 0, "2")), first.correctOptionIds());
        student.expectError(MessageType.ANSWER_QUESTION,
                new AnswerRequest(p.practiceId(), 0, List.of(optionId(p, 0, "2")), null), ErrorCode.ALREADY_ANSWERED);

        AnswerResult tf = student.request(MessageType.ANSWER_QUESTION,
                new AnswerRequest(p.practiceId(), 2, null, true), MessageType.ANSWER_RESULT).payloadAs(AnswerResult.class);
        assertTrue(tf.correct());
        assertEquals("0 可被 2 整除", tf.explanation());

        // 結束練習：送出空清單，以已作答的為準；第 2 題未作答 0 分
        PracticeResult result = submit(student, p, List.of());
        assertEquals(List.of(0.0, 0.0, 1.0), result.questions().stream().map(ResultItem::score).toList());
        assertNotNull(result.questions().get(0).answeredAt());
        assertEquals(1, result.record().correctCount());
    }

    @Test
    void finishedOrReplacedPracticeCannotBeUsed() throws Exception {
        PracticeStarted old = start(student, fullExam(RevealMode.IMMEDIATE));
        PracticeStarted current = start(student, fullExam(RevealMode.AFTER_SUBMIT));

        student.expectError(MessageType.SUBMIT_PRACTICE, new SubmitRequest(old.practiceId(), List.of()),
                ErrorCode.PRACTICE_NOT_FOUND);
        submit(student, current, List.of());
        student.expectError(MessageType.SUBMIT_PRACTICE, new SubmitRequest(current.practiceId(), List.of()),
                ErrorCode.PRACTICE_NOT_FOUND);
        owner.expectError(MessageType.SUBMIT_PRACTICE, new SubmitRequest(current.practiceId(), List.of()),
                ErrorCode.PRACTICE_NOT_FOUND);
    }

    // ---------- 隨機抽題

    @Test
    void randomModeRespectsCountAndScope() throws Exception {
        assertEquals(2, start(student, random(examId, null, 2)).questions().size());
        assertEquals(3, start(student, random(null, math, 50)).questions().size(), "題庫不足時全部使用");
        assertEquals(3, start(student, random(null, null, null)).questions().size());
        assertTrue(start(student, random(null, null, 1)).title().startsWith("隨機抽題"));

        long english = owner.request(MessageType.LIST_SUBJECTS, null, MessageType.SUBJECTS).payloadAs(SubjectList.class)
                .subjects().stream().filter(s -> s.name().equals("英文")).findFirst().orElseThrow().id();
        student.expectError(MessageType.START_PRACTICE, random(null, english, 5), ErrorCode.NO_QUESTIONS);
        student.expectError(MessageType.START_PRACTICE, random(null, null, 0), ErrorCode.INVALID_PAYLOAD);
        student.expectError(MessageType.START_PRACTICE, random(null, null, 101), ErrorCode.INVALID_PAYLOAD);
        student.expectError(MessageType.START_PRACTICE,
                new StartPracticeRequest(PracticeMode.FULL_EXAM, RevealMode.IMMEDIATE, null, null, null),
                ErrorCode.INVALID_PAYLOAD);
    }

    // ---------- 錯題本

    @Test
    void wrongBookAccumulatesAndOnlyManualRemovalDeletes() throws Exception {
        PracticeStarted p = start(student, fullExam(RevealMode.AFTER_SUBMIT));
        submit(student, p, List.of(pick(p, 0, "2"))); // 第 2、3 題錯

        PracticeStarted review = start(student, new StartPracticeRequest(PracticeMode.WRONG_REVIEW,
                RevealMode.AFTER_SUBMIT, null, null, null));
        assertEquals(2, review.questions().size());
        List<AnswerData> allCorrect = new ArrayList<>();
        for (PracticeQuestion q : review.questions()) {
            allCorrect.add(q.type() == QuestionType.TRUE_FALSE
                    ? new AnswerData(q.index(), null, true)
                    : new AnswerData(q.index(), List.of(optionId(review, q.index(), "2"), optionId(review, q.index(), "3")), null));
        }
        assertEquals(100.0, submit(student, review, allCorrect).record().score());
        assertEquals(2, wrongContents(student, null).size(), "答對不會自動移除");

        PracticeStarted again = start(student, fullExam(RevealMode.AFTER_SUBMIT));
        submit(student, again, List.of());
        WrongList book = student.request(MessageType.LIST_WRONG, new ListWrongRequest(null), MessageType.WRONG_LIST)
                .payloadAs(WrongList.class);
        WrongItem single = book.items().stream().filter(i -> i.question().content().equals("單選 1+1")).findFirst().orElseThrow();
        WrongItem multi = book.items().stream().filter(i -> i.question().content().equals("多選 質數")).findFirst().orElseThrow();
        assertEquals(1, single.wrongCount());
        assertEquals(2, multi.wrongCount());
        assertEquals("數學", multi.subject().name());
        assertEquals("期中考", multi.examTitle());
        assertTrue(multi.question().options().get(0).correct(), "錯題本顯示正確答案");
        assertEquals(3, wrongContents(student, math).size());

        for (WrongItem item : book.items()) {
            student.request(MessageType.REMOVE_WRONG, new WrongRef(item.questionId()), MessageType.WRONG_REMOVED);
        }
        assertTrue(wrongContents(student, null).isEmpty());
        student.expectError(MessageType.REMOVE_WRONG, new WrongRef(single.questionId()), ErrorCode.QUESTION_NOT_FOUND);
        student.expectError(MessageType.START_PRACTICE, new StartPracticeRequest(PracticeMode.WRONG_REVIEW,
                RevealMode.IMMEDIATE, null, null, null), ErrorCode.NO_QUESTIONS);
    }

    // ---------- 紀錄

    @Test
    void recordsArePrivateAndKeepSnapshotsAfterDeletion() throws Exception {
        PracticeStarted p = start(student, fullExam(RevealMode.AFTER_SUBMIT));
        long recordId = submit(student, p, List.of(pick(p, 0, "2"))).record().id();

        owner.expectError(MessageType.GET_RECORD, new RecordRef(recordId), ErrorCode.RECORD_NOT_FOUND);
        assertTrue(owner.request(MessageType.LIST_RECORDS, null, MessageType.RECORDS)
                .payloadAs(RecordList.class).records().isEmpty());

        owner.request(MessageType.DELETE_EXAM, new ExamRef(examId), MessageType.EXAM_DELETED);

        PracticeResult kept = student.request(MessageType.GET_RECORD, new RecordRef(recordId), MessageType.PRACTICE_RESULT)
                .payloadAs(PracticeResult.class);
        assertEquals(List.of("單選 1+1", "多選 質數", "是非 0 是偶數"),
                kept.questions().stream().map(r -> r.question().content()).toList());
        assertEquals(33.3, kept.record().score());
        assertTrue(wrongContents(student, null).isEmpty(), "題目刪除後錯題一併消失");
    }

    @Test
    void practiceRequiresLogin() throws Exception {
        TestClient anonymous = connect();
        anonymous.hello();

        anonymous.expectError(MessageType.START_PRACTICE, fullExam(RevealMode.IMMEDIATE), ErrorCode.NOT_LOGGED_IN);
        anonymous.expectError(MessageType.LIST_RECORDS, null, ErrorCode.NOT_LOGGED_IN);
    }

    // ---------- 輔助方法

    private void add(QuestionData q) throws Exception {
        owner.request(MessageType.ADD_QUESTION, new QuestionRequest(examId, null, q), MessageType.QUESTION_SAVED);
    }

    private StartPracticeRequest fullExam(RevealMode reveal) {
        return new StartPracticeRequest(PracticeMode.FULL_EXAM, reveal, examId, null, null);
    }

    private static StartPracticeRequest random(Long exam, Long subject, Integer count) {
        return new StartPracticeRequest(PracticeMode.RANDOM, RevealMode.AFTER_SUBMIT, exam, subject, count);
    }

    private static PracticeStarted start(TestClient c, StartPracticeRequest request) throws Exception {
        return c.request(MessageType.START_PRACTICE, request, MessageType.PRACTICE_STARTED).payloadAs(PracticeStarted.class);
    }

    private static PracticeResult submit(TestClient c, PracticeStarted p, List<AnswerData> answers) throws Exception {
        return c.request(MessageType.SUBMIT_PRACTICE, new SubmitRequest(p.practiceId(), answers),
                MessageType.PRACTICE_RESULT).payloadAs(PracticeResult.class);
    }

    private static List<String> wrongContents(TestClient c, Long subjectId) throws Exception {
        return c.request(MessageType.LIST_WRONG, new ListWrongRequest(subjectId), MessageType.WRONG_LIST)
                .payloadAs(WrongList.class).items().stream().map(i -> i.question().content()).sorted().toList();
    }

    private static long optionId(PracticeStarted p, int index, String content) {
        return p.questions().get(index).options().stream().filter(o -> o.content().equals(content))
                .findFirst().orElseThrow().id();
    }

    private static AnswerData pick(PracticeStarted p, int index, String... contents) {
        List<Long> ids = new ArrayList<>();
        for (String c : contents) {
            ids.add(optionId(p, index, c));
        }
        return new AnswerData(index, ids, null);
    }

    /** 選項以 * 結尾表示正確答案。 */
    private static QuestionData choice(QuestionType type, String content, String... options) {
        List<OptionData> list = new ArrayList<>();
        for (String o : options) {
            boolean correct = o.endsWith("*");
            list.add(new OptionData(null, correct ? o.substring(0, o.length() - 1) : o, correct));
        }
        return new QuestionData(null, type, content, null, list, null, false);
    }
}
