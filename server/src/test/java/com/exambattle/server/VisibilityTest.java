package com.exambattle.server;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.bank.ExamList;
import com.exambattle.common.protocol.bank.ExamRef;
import com.exambattle.common.protocol.bank.ExamRequest;
import com.exambattle.common.protocol.bank.ExamSummary;
import com.exambattle.common.protocol.bank.ListExamsRequest;
import com.exambattle.common.protocol.bank.OptionData;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionRequest;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.bank.SubjectList;
import com.exambattle.common.protocol.bank.TakeDownRequest;
import com.exambattle.common.protocol.bank.Visibility;
import com.exambattle.common.protocol.game.StartGameRequest;
import com.exambattle.common.protocol.payload.LoginOk;
import com.exambattle.common.protocol.practice.PracticeMode;
import com.exambattle.common.protocol.practice.RevealMode;
import com.exambattle.common.protocol.practice.StartPracticeRequest;
import com.exambattle.server.dao.InMemoryUserDao;
import com.exambattle.server.dao.TestRepositories;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第八階段：公開／私人題庫、搜尋、管理員下架（D-039、D-040）。 */
class VisibilityTest extends ServerTestBase {

    private final InMemoryUserDao users = new InMemoryUserDao();
    private TestClient owner;
    private TestClient other;
    private TestClient admin;
    private long math;

    @BeforeEach
    void setUp() throws Exception {
        restartServer(testConfig(), TestRepositories.inMemory(users));
        owner = loggedIn("teacher");
        other = loggedIn("student");
        admin = connect();
        admin.hello();
        admin.register("boss", "管理員");
        users.grantAdmin("boss");
        assertTrue(admin.request(MessageType.LOGIN, new com.exambattle.common.protocol.payload.LoginRequest("boss",
                TestClient.PASSWORD), MessageType.LOGIN_OK).payloadAs(LoginOk.class).admin());
        math = owner.request(MessageType.LIST_SUBJECTS, null, MessageType.SUBJECTS).payloadAs(SubjectList.class)
                .subjects().stream().filter(s -> s.name().equals("數學")).findFirst().orElseThrow().id();
    }

    @Test
    void newExamsArePrivateAndHiddenFromOthers() throws Exception {
        ExamSummary exam = create("期末考", 2024, "段考", null);
        assertEquals(Visibility.PRIVATE, exam.visibility());
        addQuestion(exam.id());

        assertEquals(List.of("期末考"), titles(owner, new ListExamsRequest(null)));
        assertTrue(titles(other, new ListExamsRequest(null)).isEmpty(), "私人考卷不出現在別人的清單");
        other.expectError(MessageType.GET_EXAM, new ExamRef(exam.id()), ErrorCode.EXAM_NOT_FOUND);
        other.expectError(MessageType.UPDATE_EXAM, new ExamRequest(exam.id(), "偷改", math, null, null),
                ErrorCode.EXAM_NOT_FOUND);
        other.expectError(MessageType.START_PRACTICE,
                new StartPracticeRequest(PracticeMode.FULL_EXAM, RevealMode.AFTER_SUBMIT, exam.id(), null, null),
                ErrorCode.EXAM_NOT_FOUND);
        other.expectError(MessageType.START_PRACTICE,
                new StartPracticeRequest(PracticeMode.RANDOM, RevealMode.AFTER_SUBMIT, null, null, 5),
                ErrorCode.NO_QUESTIONS);
        String code = other.createRoom().roomCode();
        other.expectError(MessageType.START_GAME, new StartGameRequest(exam.id(), null, false, null, 10),
                ErrorCode.EXAM_NOT_FOUND);
        other.expectError(MessageType.START_GAME, new StartGameRequest(null, null, true, null, 10),
                ErrorCode.NO_QUESTIONS);
        assertFalse(code.isEmpty());

        ExamSummary shared = owner.request(MessageType.UPDATE_EXAM,
                new ExamRequest(exam.id(), "期末考", math, 2024, "段考", Visibility.PUBLIC), MessageType.EXAM_SAVED)
                .payloadAs(ExamSummary.class);
        assertEquals(Visibility.PUBLIC, shared.visibility());
        assertEquals(List.of("期末考"), titles(other, new ListExamsRequest(null)));
        other.request(MessageType.START_PRACTICE,
                new StartPracticeRequest(PracticeMode.FULL_EXAM, RevealMode.AFTER_SUBMIT, exam.id(), null, null),
                MessageType.PRACTICE_STARTED);

        ExamSummary renamed = owner.request(MessageType.UPDATE_EXAM, new ExamRequest(exam.id(), "期末考 A", math, null, null),
                MessageType.EXAM_SAVED).payloadAs(ExamSummary.class);
        assertEquals(Visibility.PUBLIC, renamed.visibility(), "沒有指定時不改變公開範圍");
    }

    @Test
    void searchByKeywordSubjectAndMine() throws Exception {
        create("113 學年度期中考", 2024, "段考", Visibility.PUBLIC);
        create("模擬考第一回", 2023, "模擬考", Visibility.PUBLIC);
        create("私人筆記", null, null, null);
        TestClient c = other;

        assertEquals(List.of("113 學年度期中考"), titles(c, new ListExamsRequest(null, "期中", null)));
        assertEquals(List.of("模擬考第一回"), titles(c, new ListExamsRequest(null, "2023", null)), "搜尋年份");
        assertEquals(List.of("模擬考第一回"), titles(c, new ListExamsRequest(null, "模擬", null)), "搜尋類型");
        assertTrue(titles(c, new ListExamsRequest(null, "私人", null)).isEmpty(), "搜不到別人的私人考卷");
        assertEquals(List.of("私人筆記"), titles(owner, new ListExamsRequest(null, "私人", null)));
        assertTrue(titles(c, new ListExamsRequest(null, "100%", null)).isEmpty(), "% 當成一般文字");
        assertTrue(titles(c, new ListExamsRequest(math + 999, null, null)).isEmpty());
        assertEquals(3, titles(owner, new ListExamsRequest(null, null, true)).size());
        assertTrue(titles(c, new ListExamsRequest(null, null, true)).isEmpty(), "只看自己的");
        c.expectError(MessageType.LIST_EXAMS, new ListExamsRequest(null, "x".repeat(51), null), ErrorCode.INVALID_PAYLOAD);
    }

    @Test
    void adminTakesDownAndRestores() throws Exception {
        ExamSummary exam = create("不當內容", null, null, Visibility.PUBLIC);
        ExamSummary privateExam = create("私人考卷", null, null, null);

        owner.expectError(MessageType.TAKE_DOWN_EXAM, new TakeDownRequest(exam.id(), true), ErrorCode.NOT_ADMIN);
        admin.expectError(MessageType.TAKE_DOWN_EXAM, new TakeDownRequest(privateExam.id(), true), ErrorCode.INVALID_PAYLOAD);

        ExamSummary down = admin.request(MessageType.TAKE_DOWN_EXAM, new TakeDownRequest(exam.id(), true),
                MessageType.EXAM_SAVED).payloadAs(ExamSummary.class);
        assertTrue(down.takenDown());
        assertEquals(Visibility.PRIVATE, down.visibility());
        assertTrue(titles(other, new ListExamsRequest(null)).isEmpty());
        assertEquals(List.of("不當內容"), titles(admin, new ListExamsRequest(null)), "管理員看得到被下架的考卷");
        admin.request(MessageType.GET_EXAM, new ExamRef(exam.id()), MessageType.EXAM_DETAIL);
        assertTrue(titles(owner, new ListExamsRequest(null)).contains("不當內容"), "建立者仍看得到");
        owner.expectError(MessageType.UPDATE_EXAM, new ExamRequest(exam.id(), "不當內容", math, null, null, Visibility.PUBLIC),
                ErrorCode.EXAM_TAKEN_DOWN);

        ExamSummary restored = admin.request(MessageType.TAKE_DOWN_EXAM, new TakeDownRequest(exam.id(), false),
                MessageType.EXAM_SAVED).payloadAs(ExamSummary.class);
        assertFalse(restored.takenDown());
        assertEquals(Visibility.PRIVATE, restored.visibility(), "解除下架後仍是私人，由建立者決定");
        owner.request(MessageType.UPDATE_EXAM, new ExamRequest(exam.id(), "改好的內容", math, null, null, Visibility.PUBLIC),
                MessageType.EXAM_SAVED);
        assertEquals(List.of("改好的內容"), titles(other, new ListExamsRequest(null)));
    }

    // ---------- 輔助

    private ExamSummary create(String title, Integer year, String type, Visibility visibility) throws Exception {
        return owner.request(MessageType.CREATE_EXAM, new ExamRequest(null, title, math, year, type, visibility),
                MessageType.EXAM_SAVED).payloadAs(ExamSummary.class);
    }

    private void addQuestion(long examId) throws Exception {
        owner.request(MessageType.ADD_QUESTION, new QuestionRequest(examId, null, new QuestionData(null,
                QuestionType.SINGLE_CHOICE, "1 + 1", null,
                List.of(new OptionData(null, "2", true), new OptionData(null, "3", false)), null, false)),
                MessageType.QUESTION_SAVED);
    }

    private static List<String> titles(TestClient c, ListExamsRequest request) throws Exception {
        return c.request(MessageType.LIST_EXAMS, request, MessageType.EXAMS).payloadAs(ExamList.class).exams().stream()
                .map(ExamSummary::title).toList();
    }
}
