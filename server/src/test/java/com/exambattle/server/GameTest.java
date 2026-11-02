package com.exambattle.server;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageCodec;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.bank.ExamRequest;
import com.exambattle.common.protocol.bank.ExamSummary;
import com.exambattle.common.protocol.bank.OptionData;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionRequest;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.bank.SubjectList;
import com.exambattle.common.protocol.game.AnswerAccepted;
import com.exambattle.common.protocol.game.GameEndReason;
import com.exambattle.common.protocol.game.GameOver;
import com.exambattle.common.protocol.game.GameProgress;
import com.exambattle.common.protocol.game.GameQuestion;
import com.exambattle.common.protocol.game.GameStarted;
import com.exambattle.common.protocol.game.KickRequest;
import com.exambattle.common.protocol.game.Kicked;
import com.exambattle.common.protocol.game.LeaderboardEntry;
import com.exambattle.common.protocol.game.QuestionResult;
import com.exambattle.common.protocol.game.StartGameRequest;
import com.exambattle.common.protocol.game.SubmitAnswerRequest;
import com.exambattle.common.protocol.payload.RoomLeaveReason;
import com.exambattle.common.protocol.payload.RoomPlayerLeft;
import com.exambattle.common.protocol.payload.RoomState;
import com.exambattle.common.protocol.payload.UserInfo;
import com.exambattle.common.protocol.practice.ListWrongRequest;
import com.exambattle.common.protocol.practice.PracticeOption;
import com.exambattle.common.protocol.practice.WrongItem;
import com.exambattle.common.protocol.practice.WrongList;
import com.exambattle.server.game.GameTiming;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 第四階段：多人競賽（經由協定測試）。時間縮短：「1 秒」= 40 毫秒。 */
class GameTest extends ServerTestBase {

    private static final GameTiming FAST = new GameTiming(Duration.ofMillis(40), Duration.ofMillis(50),
            Duration.ofMillis(100), Duration.ofMillis(50));

    private TestClient owner;
    private TestClient student;
    private UserInfo ownerInfo;
    private UserInfo studentInfo;
    private long examId;
    private String roomCode;

    @BeforeEach
    void setUp() throws Exception {
        restartServer(testConfig().withGameTiming(FAST));
        owner = connect();
        ownerInfo = owner.signUpAndLogin("teacher");
        student = connect();
        studentInfo = student.signUpAndLogin("student");
        long math = owner.request(MessageType.LIST_SUBJECTS, null, MessageType.SUBJECTS).payloadAs(SubjectList.class)
                .subjects().stream().filter(s -> s.name().equals("數學")).findFirst().orElseThrow().id();
        examId = owner.request(MessageType.CREATE_EXAM, new ExamRequest(null, "競賽卷", math, null, null),
                MessageType.EXAM_SAVED).payloadAs(ExamSummary.class).id();
        add(choice(QuestionType.SINGLE_CHOICE, "1 + 1", "1", "2*"));
        add(choice(QuestionType.SINGLE_CHOICE, "2 + 2", "4*", "5"));
        add(choice(QuestionType.SINGLE_CHOICE, "3 + 3", "6*", "7"));
        add(choice(QuestionType.SINGLE_CHOICE, "答案未設定", "甲", "乙"));
        roomCode = owner.createRoom().roomCode();
        student.joinRoom(roomCode);
    }

    @Test
    void fullGameWithStreakBonusTimeoutAndWrongBook() throws Exception {
        GameStarted started = start(new StartGameRequest(examId, null, false, null, 10));
        assertEquals(3, started.questionCount(), "未設定答案的題目不出");
        assertEquals(List.of("teacher", "student"), started.players().stream().map(UserInfo::username).toList());
        assertEquals(started.gameId(), student.nextOfType(MessageType.GAME_STARTED).payloadAs(GameStarted.class).gameId());

        // 第 1 題：房主答對、學生答錯
        GameQuestion q1 = question(owner);
        assertEquals(q1, question(student), "所有人收到同一題");
        assertEquals(400, q1.durationMs());
        answer(owner, q1, "2");
        answer(student, q1, "1");
        QuestionResult r1 = result(owner);
        assertEquals(1000, r1.you().points());
        assertEquals(1, r1.you().streak());
        assertEquals(List.of(optionId(q1, "2")), r1.correctOptionIds());
        assertEquals(0, result(student).you().points());
        assertEquals(List.of("teacher", "student"), r1.leaderboard().stream().map(e -> e.player().username()).toList());

        // 第 2 題：兩人都答對，房主連續答對 +100
        GameQuestion q2 = question(owner);
        question(student);
        answer(owner, q2, "4");
        answer(student, q2, "4");
        QuestionResult r2 = result(owner);
        assertEquals(1100, r2.you().points());
        assertEquals(100, r2.you().bonus());
        assertEquals(1000, result(student).you().points());

        // 第 3 題：學生答對，房主沒作答（時間到才公布）
        GameQuestion q3 = question(owner);
        question(student);
        answer(student, q3, "6");
        QuestionResult r3 = result(owner);
        assertFalse(r3.you().answered());
        assertEquals(0, r3.you().streak(), "沒作答，連續答對歸零");
        assertEquals(1100, result(student).you().points());
        owner.expectError(MessageType.SUBMIT_ANSWER, submit(q3, optionId(q3, "6")), ErrorCode.TIME_UP);

        GameOver over = owner.nextOfType(MessageType.GAME_OVER).payloadAs(GameOver.class);
        assertEquals(GameEndReason.FINISHED, over.reason());
        assertEquals(3, over.questionsPlayed());
        List<LeaderboardEntry> board = over.leaderboard();
        assertEquals(List.of(2100, 2100), board.stream().map(LeaderboardEntry::score).toList());
        assertEquals(List.of(1, 1), board.stream().map(LeaderboardEntry::rank).toList(), "同分同名次");
        assertEquals(1, over.wrongAdded());
        assertEquals(1, student.nextOfType(MessageType.GAME_OVER).payloadAs(GameOver.class).wrongAdded());

        assertEquals(List.of("3 + 3"), waitForWrongs(owner, 1));
        assertEquals(List.of("1 + 1"), waitForWrongs(student, 1));
        assertFalse(owner.request(MessageType.GET_ROOM, null, MessageType.ROOM_STATE).payloadAs(RoomState.class).playing(),
                "結束後房間回到等待中，可以再開一場");
    }

    @Test
    void questionsHideAnswersAndProgressIsBroadcast() throws Exception {
        start(new StartGameRequest(examId, null, false, 1, 10));
        Message raw = owner.nextOfType(MessageType.GAME_QUESTION);
        String json = MessageCodec.encode(raw);
        assertFalse(json.contains("correct") || json.contains("trueFalseAnswer") || json.contains("explanation"),
                "出題時不可傳送答案：" + json);
        GameQuestion q = raw.payloadAs(GameQuestion.class);
        question(student);

        answer(student, q, "1");
        GameProgress progress = owner.nextOfType(MessageType.GAME_PROGRESS).payloadAs(GameProgress.class);
        assertEquals(1, progress.answered());
        assertEquals(2, progress.players());
        student.expectError(MessageType.SUBMIT_ANSWER, submit(q, optionId(q, "2")), ErrorCode.ALREADY_ANSWERED);
        student.expectError(MessageType.SUBMIT_ANSWER, new SubmitAnswerRequest(q.gameId(), 5, List.of(), null),
                ErrorCode.WRONG_QUESTION);
    }

    @Test
    void multipleChoiceGetsPartialPointsWithoutStreak() throws Exception {
        add(choice(QuestionType.MULTIPLE_CHOICE, "質數", "2*", "3*", "4", "6", "8"));
        long multiExam = examId;
        // 只出最後一題：隨機抽 1 題時無法指定，改用依序出全部題目後在第 4 題作答
        start(new StartGameRequest(multiExam, null, false, null, 10));
        for (int i = 0; i < 3; i++) {
            GameQuestion q = question(owner);
            question(student);
            answer(owner, q, q.question().options().get(i == 0 ? 1 : 0).content());
            answer(student, q, q.question().options().get(i == 0 ? 1 : 0).content());
            result(owner);
            result(student);
        }
        GameQuestion q4 = question(owner);
        question(student);
        answer(owner, q4, "2"); // n=5、k=1 → 0.6
        answer(student, q4, "2", "3");
        QuestionResult r = result(owner);
        assertEquals(600, r.you().points());
        assertFalse(r.you().correct());
        assertEquals(0, r.you().streak());
        QuestionResult s = result(student);
        assertEquals(1300, s.you().points(), "連續第 4 題答對：1000 + 300");
    }

    @Test
    void onlyOwnerStartsWithValidSettings() throws Exception {
        student.expectError(MessageType.START_GAME, new StartGameRequest(examId, null, false, null, 10),
                ErrorCode.NOT_ROOM_OWNER);
        owner.expectError(MessageType.START_GAME, new StartGameRequest(examId, null, false, null, 15),
                ErrorCode.INVALID_PAYLOAD);
        owner.expectError(MessageType.START_GAME, new StartGameRequest(examId, null, true, 0, 10),
                ErrorCode.INVALID_PAYLOAD);
        owner.expectError(MessageType.START_GAME, new StartGameRequest(9999L, null, false, null, 10),
                ErrorCode.EXAM_NOT_FOUND);
        TestClient lonely = loggedIn("lonely");
        lonely.expectError(MessageType.START_GAME, new StartGameRequest(examId, null, false, null, 10),
                ErrorCode.NOT_IN_ROOM);

        long empty = owner.request(MessageType.CREATE_EXAM, new ExamRequest(null, "空卷", math(), null, null),
                MessageType.EXAM_SAVED).payloadAs(ExamSummary.class).id();
        owner.expectError(MessageType.START_GAME, new StartGameRequest(empty, null, false, null, 10),
                ErrorCode.NO_QUESTIONS);

        start(new StartGameRequest(examId, null, true, 2, 10));
        owner.expectError(MessageType.START_GAME, new StartGameRequest(examId, null, false, null, 10),
                ErrorCode.GAME_IN_PROGRESS);
        lonely.expectError(MessageType.JOIN_ROOM, new com.exambattle.common.protocol.payload.JoinRoomRequest(roomCode),
                ErrorCode.GAME_IN_PROGRESS);
        owner.expectError(MessageType.KICK_PLAYER, new KickRequest(studentInfo.userId()), ErrorCode.GAME_IN_PROGRESS);
    }

    @Test
    void ownerKicksPlayersBeforeTheGame() throws Exception {
        TestClient third = loggedIn("third");
        third.joinRoom(roomCode);

        student.expectError(MessageType.KICK_PLAYER, new KickRequest(ownerInfo.userId()), ErrorCode.NOT_ROOM_OWNER);
        owner.expectError(MessageType.KICK_PLAYER, new KickRequest(99_999L), ErrorCode.PLAYER_NOT_FOUND);
        owner.expectError(MessageType.KICK_PLAYER, new KickRequest(ownerInfo.userId()), ErrorCode.INVALID_PAYLOAD);

        RoomState after = owner.request(MessageType.KICK_PLAYER, new KickRequest(studentInfo.userId()),
                MessageType.ROOM_STATE).payloadAs(RoomState.class);
        assertEquals(List.of("teacher", "third"), after.players().stream().map(UserInfo::username).toList());
        assertEquals(roomCode, student.nextOfType(MessageType.KICKED).payloadAs(Kicked.class).roomCode());
        RoomPlayerLeft left = third.nextOfType(MessageType.ROOM_PLAYER_LEFT).payloadAs(RoomPlayerLeft.class);
        assertEquals(RoomLeaveReason.KICKED, left.reason());
        student.expectError(MessageType.GET_ROOM, null, ErrorCode.NOT_IN_ROOM);
        student.joinRoom(roomCode); // 可以再加入
    }

    @Test
    void leavingPlayerClosesQuestionEarlyAndOwnerCanEndTheGame() throws Exception {
        start(new StartGameRequest(examId, null, false, null, 60));
        GameQuestion q = question(owner);
        question(student);
        answer(owner, q, "2");
        student.request(MessageType.LEAVE_ROOM, null, MessageType.LEFT_ROOM);

        QuestionResult r = result(owner);
        assertEquals(List.of("teacher"), r.leaderboard().stream().map(e -> e.player().username()).toList(),
                "離開的玩家不在排行榜上，剩下的人都作答完就公布");

        question(owner);
        GameOver over = owner.request(MessageType.END_GAME, null, MessageType.GAME_OVER).payloadAs(GameOver.class);
        assertEquals(GameEndReason.ENDED_BY_OWNER, over.reason());
        assertEquals(1, over.questionsPlayed());
        owner.assertNoMessageOfType(MessageType.GAME_QUESTION, 300);
        start(new StartGameRequest(examId, null, false, 1, 10)); // 可以再開一場
    }

    @Test
    void ownerDisconnectAbortsTheGame() throws Exception {
        start(new StartGameRequest(examId, null, false, null, 10));
        question(student);
        owner.close();

        student.nextOfType(MessageType.ROOM_CLOSED);
        student.assertNoMessageOfType(MessageType.GAME_QUESTION, 600);
    }

    // ---------- 輔助

    private long math() throws Exception {
        return owner.request(MessageType.LIST_SUBJECTS, null, MessageType.SUBJECTS).payloadAs(SubjectList.class)
                .subjects().stream().filter(s -> s.name().equals("數學")).findFirst().orElseThrow().id();
    }

    private GameStarted start(StartGameRequest request) throws Exception {
        return owner.request(MessageType.START_GAME, request, MessageType.GAME_STARTED).payloadAs(GameStarted.class);
    }

    private static GameQuestion question(TestClient c) throws Exception {
        return c.nextOfType(MessageType.GAME_QUESTION).payloadAs(GameQuestion.class);
    }

    private static QuestionResult result(TestClient c) throws Exception {
        return c.nextOfType(MessageType.QUESTION_RESULT).payloadAs(QuestionResult.class);
    }

    private static void answer(TestClient c, GameQuestion q, String... contents) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (String content : contents) {
            ids.add(optionId(q, content));
        }
        AnswerAccepted ok = c.request(MessageType.SUBMIT_ANSWER, submit(q, ids.toArray(Long[]::new)),
                MessageType.ANSWER_ACCEPTED).payloadAs(AnswerAccepted.class);
        assertEquals(q.index(), ok.index());
    }

    private static SubmitAnswerRequest submit(GameQuestion q, Long... optionIds) {
        return new SubmitAnswerRequest(q.gameId(), q.index(), List.of(optionIds), null);
    }

    private static long optionId(GameQuestion q, String content) {
        return q.question().options().stream().filter(o -> o.content().equals(content)).findFirst()
                .map(PracticeOption::id).orElseThrow();
    }

    /** 錯題本在背景寫入，稍等一下。 */
    private static List<String> waitForWrongs(TestClient c, int expected) throws Exception {
        List<String> contents = List.of();
        for (int i = 0; i < 50 && contents.size() < expected; i++) {
            contents = c.request(MessageType.LIST_WRONG, new ListWrongRequest(null), MessageType.WRONG_LIST)
                    .payloadAs(WrongList.class).items().stream().map(WrongItem::question)
                    .map(QuestionData::content).toList();
            if (contents.size() < expected) {
                Thread.sleep(20);
            }
        }
        return contents;
    }

    private void add(QuestionData q) throws Exception {
        owner.request(MessageType.ADD_QUESTION, new QuestionRequest(examId, null, q), MessageType.QUESTION_SAVED);
    }

    /** 選項以 * 結尾表示正確答案；沒有 * 表示答案未設定。 */
    private static QuestionData choice(QuestionType type, String content, String... options) {
        List<OptionData> list = new ArrayList<>();
        for (String o : options) {
            boolean correct = o.endsWith("*");
            list.add(new OptionData(null, correct ? o.substring(0, o.length() - 1) : o, correct));
        }
        return new QuestionData(null, type, content, null, list, null, false);
    }
}
