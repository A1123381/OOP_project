package com.exambattle.server;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.bank.ExamDetail;
import com.exambattle.common.protocol.bank.ExamRef;
import com.exambattle.common.protocol.bank.ExamRequest;
import com.exambattle.common.protocol.bank.ExamSummary;
import com.exambattle.common.protocol.bank.GroupRequest;
import com.exambattle.common.protocol.bank.GroupSaved;
import com.exambattle.common.protocol.bank.ImportDone;
import com.exambattle.common.protocol.bank.ImportRequest;
import com.exambattle.common.protocol.bank.OptionData;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionRef;
import com.exambattle.common.protocol.bank.QuestionRequest;
import com.exambattle.common.protocol.bank.QuestionSaved;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.bank.SubjectList;
import com.exambattle.common.protocol.practice.AnswerData;
import com.exambattle.common.protocol.practice.ListWrongRequest;
import com.exambattle.common.protocol.practice.PracticeMode;
import com.exambattle.common.protocol.practice.PracticeQuestion;
import com.exambattle.common.protocol.practice.PracticeResult;
import com.exambattle.common.protocol.practice.PracticeStarted;
import com.exambattle.common.protocol.practice.RevealMode;
import com.exambattle.common.protocol.practice.StartPracticeRequest;
import com.exambattle.common.protocol.practice.SubmitRequest;
import com.exambattle.common.protocol.practice.WrongList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 題組（D-034）：多題共用一篇文章，經由協定測試。 */
class GroupTest extends ServerTestBase {

    private TestClient owner;
    private TestClient student;
    private long math;
    private long examId;

    @BeforeEach
    void createExam() throws Exception {
        owner = loggedIn("teacher");
        student = loggedIn("student");
        math = owner.request(MessageType.LIST_SUBJECTS, null, MessageType.SUBJECTS).payloadAs(SubjectList.class)
                .subjects().stream().filter(s -> s.name().equals("英文")).findFirst().orElseThrow().id();
        examId = newExam("克漏字");
    }

    @Test
    void addCreatesAndJoinsGroupAndEveryoneSeesPassage() throws Exception {
        QuestionData first = add(examId, blank(1).withGroup(null, " 文章【1】與【2】 "));
        assertNotNull(first.groupId());
        assertEquals("文章【1】與【2】", first.passage(), "去除前後空白");

        QuestionData second = add(examId, blank(2).withGroup(first.groupId(), "這段會被忽略"));
        assertEquals(first.groupId(), second.groupId());
        assertEquals("文章【1】與【2】", second.passage(), "加入既有題組時沿用原文章");
        add(examId, blank(3));

        ExamDetail seen = detail(student, examId);
        assertEquals(Arrays.asList("文章【1】與【2】", "文章【1】與【2】", null),
                seen.questions().stream().map(QuestionData::passage).toList(), "非建立者也看得到文章");
    }

    @Test
    void updateGroupChangesAllQuestionsAndChecksOwnership() throws Exception {
        QuestionData first = add(examId, blank(1).withGroup(null, "舊文章"));
        add(examId, blank(2).withGroup(first.groupId(), null));

        GroupSaved saved = owner.request(MessageType.UPDATE_GROUP, new GroupRequest(examId, first.groupId(), "新文章"),
                MessageType.GROUP_SAVED).payloadAs(GroupSaved.class);
        assertEquals("新文章", saved.passage());
        assertEquals(List.of("新文章", "新文章"),
                detail(owner, examId).questions().stream().map(QuestionData::passage).toList());

        student.expectError(MessageType.UPDATE_GROUP, new GroupRequest(examId, first.groupId(), "x"), ErrorCode.NOT_OWNER);
        owner.expectError(MessageType.UPDATE_GROUP, new GroupRequest(examId, first.groupId(), "  "),
                ErrorCode.INVALID_PAYLOAD);
        owner.expectError(MessageType.UPDATE_GROUP, new GroupRequest(examId, first.groupId(), "字".repeat(20_001)),
                ErrorCode.INVALID_PAYLOAD);
        owner.expectError(MessageType.UPDATE_GROUP, new GroupRequest(examId, 99_999L, "x"), ErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void groupsCannotBeJoinedAcrossExams() throws Exception {
        QuestionData first = add(examId, blank(1).withGroup(null, "文章"));
        long other = newExam("另一份");

        owner.expectError(MessageType.ADD_QUESTION, new QuestionRequest(other, null, blank(1).withGroup(first.groupId(), null)),
                ErrorCode.GROUP_NOT_FOUND);
        owner.expectError(MessageType.UPDATE_GROUP, new GroupRequest(other, first.groupId(), "x"), ErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void movingOutAndDeletingRemovesEmptyGroup() throws Exception {
        QuestionData first = add(examId, blank(1).withGroup(null, "文章"));
        QuestionData second = add(examId, blank(2).withGroup(first.groupId(), null));

        QuestionData moved = owner.request(MessageType.UPDATE_QUESTION,
                new QuestionRequest(examId, first.id(), blank(1)), MessageType.QUESTION_SAVED)
                .payloadAs(QuestionSaved.class).question();
        assertNull(moved.groupId());
        assertNull(moved.passage());

        owner.request(MessageType.DELETE_QUESTION, new QuestionRef(examId, second.id()), MessageType.QUESTION_DELETED);
        owner.expectError(MessageType.UPDATE_GROUP, new GroupRequest(examId, first.groupId(), "x"), ErrorCode.GROUP_NOT_FOUND);
    }

    @Test
    void importCreatesGroupsByIndex() throws Exception {
        ImportDone done = owner.request(MessageType.IMPORT_QUESTIONS, new ImportRequest(null,
                new ExamRequest(null, "匯入題組", math, null, null),
                List.of(blank(1), blank(2), blank(3), blank(4)),
                List.of("甲文", "乙文"), Arrays.asList(0, 0, null, 1)), MessageType.IMPORT_DONE).payloadAs(ImportDone.class);

        List<QuestionData> qs = detail(owner, done.exam().id()).questions();
        assertEquals(Arrays.asList("甲文", "甲文", null, "乙文"), qs.stream().map(QuestionData::passage).toList());
        assertEquals(qs.get(0).groupId(), qs.get(1).groupId());
    }

    @Test
    void importRejectsBadGroupReferences() throws Exception {
        ExamRequest exam = new ExamRequest(null, "不應建立", math, null, null);
        List<QuestionData> two = List.of(blank(1), blank(2));

        assertTrue(owner.expectError(MessageType.IMPORT_QUESTIONS,
                new ImportRequest(null, exam, two, List.of("文"), Arrays.asList(0, 1)), ErrorCode.INVALID_PAYLOAD)
                .message().contains("第 2 題"));
        assertTrue(owner.expectError(MessageType.IMPORT_QUESTIONS,
                new ImportRequest(null, exam, two, List.of("文", "沒人用"), Arrays.asList(0, 0)), ErrorCode.INVALID_PAYLOAD)
                .message().contains("第 2 個題組"));
        owner.expectError(MessageType.IMPORT_QUESTIONS,
                new ImportRequest(null, exam, two, List.of("文"), List.of(0)), ErrorCode.INVALID_PAYLOAD);
        assertTrue(owner.expectError(MessageType.IMPORT_QUESTIONS,
                new ImportRequest(null, exam, two, List.of(" "), Arrays.asList(0, 0)), ErrorCode.INVALID_PAYLOAD)
                .message().contains("第 1 個題組"));
    }

    @Test
    void practiceResultAndWrongBookCarryPassage() throws Exception {
        QuestionData first = add(examId, blank(1).withGroup(null, "練習用文章"));
        add(examId, blank(2));

        PracticeStarted p = student.request(MessageType.START_PRACTICE,
                new StartPracticeRequest(PracticeMode.FULL_EXAM, RevealMode.AFTER_SUBMIT, examId, null, null),
                MessageType.PRACTICE_STARTED).payloadAs(PracticeStarted.class);
        assertEquals(Arrays.asList("練習用文章", null), p.questions().stream().map(PracticeQuestion::passage).toList());
        assertEquals(first.groupId(), p.questions().get(0).groupId());

        PracticeResult result = student.request(MessageType.SUBMIT_PRACTICE,
                new SubmitRequest(p.practiceId(), List.of(new AnswerData(0, List.of(), null))), MessageType.PRACTICE_RESULT)
                .payloadAs(PracticeResult.class);
        assertEquals("練習用文章", result.questions().get(0).question().passage(), "快照帶文章");

        WrongList wrong = student.request(MessageType.LIST_WRONG, new ListWrongRequest(null), MessageType.WRONG_LIST)
                .payloadAs(WrongList.class);
        assertTrue(wrong.items().stream().anyMatch(i -> "練習用文章".equals(i.question().passage())));
    }

    // ---------- 輔助方法

    private long newExam(String title) throws Exception {
        return owner.request(MessageType.CREATE_EXAM, new ExamRequest(null, title, math, null, null),
                MessageType.EXAM_SAVED).payloadAs(ExamSummary.class).id();
    }

    private QuestionData add(long exam, QuestionData q) throws Exception {
        return owner.request(MessageType.ADD_QUESTION, new QuestionRequest(exam, null, q), MessageType.QUESTION_SAVED)
                .payloadAs(QuestionSaved.class).question();
    }

    private static ExamDetail detail(TestClient c, long exam) throws Exception {
        return c.request(MessageType.GET_EXAM, new ExamRef(exam), MessageType.EXAM_DETAIL).payloadAs(ExamDetail.class);
    }

    /** 克漏字的一格：選項甲為正確答案。 */
    private static QuestionData blank(int n) {
        return new QuestionData(null, QuestionType.SINGLE_CHOICE, "第 " + n + " 格", null,
                List.of(new OptionData(null, "甲", true), new OptionData(null, "乙", false)), null, false);
    }
}
