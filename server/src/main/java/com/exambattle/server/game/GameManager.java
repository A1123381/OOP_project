package com.exambattle.server.game;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.ProtocolException;
import com.exambattle.common.protocol.game.AnswerAccepted;
import com.exambattle.common.protocol.game.GameEndReason;
import com.exambattle.common.protocol.game.GameOver;
import com.exambattle.common.protocol.game.GameProgress;
import com.exambattle.common.protocol.game.GameQuestion;
import com.exambattle.common.protocol.game.GameStarted;
import com.exambattle.common.protocol.game.LeaderboardEntry;
import com.exambattle.common.protocol.game.PlayerResult;
import com.exambattle.common.protocol.game.QuestionResult;
import com.exambattle.common.protocol.game.StartGameRequest;
import com.exambattle.common.protocol.game.SubmitAnswerRequest;
import com.exambattle.common.protocol.practice.AnswerData;
import com.exambattle.common.protocol.practice.PracticeOption;
import com.exambattle.common.protocol.practice.PracticeQuestion;
import com.exambattle.server.Session;
import com.exambattle.server.dao.BankRecords.QuestionRecord;
import com.exambattle.server.dao.Repositories;
import com.exambattle.server.practice.Grader;
import com.exambattle.server.room.Room;
import com.exambattle.server.room.RoomManager;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 多人競賽（第四階段，D-037）。出題、計時、判分、計分與排行榜一律由伺服器負責：
 * <ul>
 *   <li>房主以 START_GAME 開始；房間內所有人（含房主）都是參賽者，競賽中不能加入或移出玩家</li>
 *   <li>每題同時送給所有人；時間到（或所有人都作答）才公布正確答案與排行榜，之後自動進入下一題</li>
 *   <li>計分見 {@link GameScoring}；結束時把每位玩家沒有全對的題目加入錯題本</li>
 * </ul>
 * 狀態與房間共用同一把鎖；計時由排程執行緒觸發，資料庫存取一律在鎖外進行。
 */
public final class GameManager implements RoomManager.Listener, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(GameManager.class.getName());

    private final Object lock;
    private final RoomManager rooms;
    private final Repositories repos;
    private final GameTiming timing;
    private final Clock clock;
    private final Random random;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService writer;
    /** 房間代碼 → 進行中的競賽。 */
    private final Map<String, Game> games = new HashMap<>();

    public GameManager(RoomManager rooms, Repositories repos, GameTiming timing, Clock clock) {
        this(rooms, repos, timing, clock, new SecureRandom());
    }

    GameManager(RoomManager rooms, Repositories repos, GameTiming timing, Clock clock, Random random) {
        this.lock = rooms.lock();
        this.rooms = rooms;
        this.repos = repos;
        this.timing = timing;
        this.clock = clock;
        this.random = random;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> daemon(r, "game-timer"));
        this.writer = Executors.newSingleThreadExecutor(r -> daemon(r, "game-writer"));
        rooms.setListener(this);
    }

    // ---------- 開始

    public void start(Session session, StartGameRequest request, String requestId) throws ProtocolException {
        if (request == null || !Protocol.GAME_SECONDS_CHOICES.contains(request.secondsPerQuestion())) {
            throw invalid("每題秒數必須是 " + Protocol.GAME_SECONDS_CHOICES + " 之一", requestId);
        }
        Integer count = request.count();
        if (count != null && (count < 1 || count > Protocol.MAX_GAME_QUESTIONS)) {
            throw invalid("題數必須介於 1–" + Protocol.MAX_GAME_QUESTIONS, requestId);
        }
        synchronized (lock) {
            requireIdle(rooms.requireOwnedRoom(session, requestId), requestId);
        }
        // 讀資料庫不持有鎖
        Selection selection = select(request, session.user().userId(), requestId);

        synchronized (lock) {
            Room room = rooms.requireOwnedRoom(session, requestId);
            requireIdle(room, requestId);
            Game game = new Game(UUID.randomUUID().toString(), room, selection.title(), selection.questions(),
                    request.secondsPerQuestion(), room.members());
            games.put(room.code(), game);
            room.setPlaying(true);
            GameStarted started = new GameStarted(game.id, room.code(), game.title, game.questions.size(),
                    game.secondsPerQuestion, game.players.values().stream().map(p -> p.user).toList(),
                    timing.startDelay().toMillis());
            for (Game.Player p : game.present()) {
                p.session.send(Message.of(MessageType.GAME_STARTED, p.session == session ? requestId : null, started));
            }
            game.pending = schedule(() -> showQuestion(game, 0), timing.startDelay());
        }
    }

    private record Selection(String title, List<QuestionRecord> questions) {
    }

    /** 選題；只用房主看得到的考卷（D-039）。 */
    private Selection select(StartGameRequest request, long hostId, String requestId) throws ProtocolException {
        List<QuestionRecord> questions;
        String title;
        int limit = request.count() != null ? request.count()
                : request.random() || request.examId() == null ? Protocol.DEFAULT_GAME_COUNT : Protocol.MAX_GAME_QUESTIONS;
        if (request.examId() != null) {
            title = repos.exams().findById(request.examId())
                    .filter(s -> s.exam().visibleTo(hostId))
                    .orElseThrow(() -> new ProtocolException(ErrorCode.EXAM_NOT_FOUND, "找不到考卷 #" + request.examId(),
                            requestId))
                    .exam().title();
            questions = new ArrayList<>(repos.questions().findByExam(request.examId()).stream()
                    .filter(QuestionRecord::answerSet).toList());
            if (request.random()) {
                Collections.shuffle(questions, random);
            }
            questions = questions.subList(0, Math.min(limit, questions.size()));
        } else {
            if (request.subjectId() != null) {
                title = repos.subjects().findById(request.subjectId())
                        .orElseThrow(() -> new ProtocolException(ErrorCode.SUBJECT_NOT_FOUND,
                                "找不到分類 #" + request.subjectId(), requestId))
                        .name();
            } else {
                title = "全部題庫";
            }
            List<Long> ids = new ArrayList<>(repos.questions().answeredIds(null, request.subjectId(), hostId));
            Collections.shuffle(ids, random);
            questions = repos.questions().findByIds(ids.subList(0, Math.min(limit, ids.size())));
        }
        if (questions.isEmpty()) {
            throw new ProtocolException(ErrorCode.NO_QUESTIONS, "沒有已設定答案的題目可以競賽", requestId);
        }
        return new Selection(request.random() || request.examId() == null ? "隨機抽題：" + title : title, questions);
    }

    // ---------- 出題與作答

    private void showQuestion(Game game, int index) {
        synchronized (lock) {
            if (!isActive(game)) {
                return;
            }
            game.phase = Game.Phase.QUESTION;
            game.index = index;
            Duration answerTime = timing.answerTime(game.secondsPerQuestion);
            game.deadline = clock.instant().plus(answerTime);
            QuestionRecord q = game.questions.get(index);
            PracticeQuestion shown = new PracticeQuestion(index, q.id(), q.type(), q.content(),
                    q.options().stream().map(o -> new PracticeOption(o.id(), o.content())).toList(),
                    q.groupId(), q.passage());
            broadcast(game, Message.of(MessageType.GAME_QUESTION, null,
                    new GameQuestion(game.id, index, game.questions.size(), shown, answerTime.toMillis())));
            game.pending = schedule(() -> closeQuestion(game, index), answerTime.plus(timing.grace()));
        }
    }

    public void submit(Session session, SubmitAnswerRequest request, String requestId) throws ProtocolException {
        synchronized (lock) {
            Room room = rooms.requireRoomOf(session, requestId);
            Game game = games.get(room.code());
            if (game == null || request.gameId() != null && !request.gameId().equals(game.id)) {
                throw new ProtocolException(ErrorCode.GAME_NOT_FOUND, "目前沒有進行中的競賽", requestId);
            }
            Game.Player player = game.players.get(session.user().userId());
            if (player == null || !player.present) {
                throw new ProtocolException(ErrorCode.GAME_NOT_FOUND, "你不是這場競賽的玩家", requestId);
            }
            if (request.index() == null) {
                throw invalid("缺少 index", requestId);
            }
            int index = request.index();
            if (index != game.index) {
                throw new ProtocolException(ErrorCode.WRONG_QUESTION, "不是目前這一題", requestId);
            }
            if (game.phase != Game.Phase.QUESTION || clock.instant().isAfter(game.deadline.plus(timing.grace()))) {
                throw new ProtocolException(ErrorCode.TIME_UP, "這一題的作答時間已結束", requestId);
            }
            if (player.answers.containsKey(index)) {
                throw new ProtocolException(ErrorCode.ALREADY_ANSWERED, "這一題已經作答過", requestId);
            }
            player.answers.put(index, new AnswerData(index, request.selectedOptionIds(), request.trueFalseAnswer()));
            session.send(Message.of(MessageType.ANSWER_ACCEPTED, requestId, new AnswerAccepted(game.id, index)));
            broadcastProgress(game);
            if (game.allAnswered()) {
                closeQuestion(game, index);
            }
        }
    }

    /** 公布一題的結果：判分、更新分數與排行榜，再排定下一題或結束。 */
    private void closeQuestion(Game game, int index) {
        synchronized (lock) {
            if (!isActive(game) || game.phase != Game.Phase.QUESTION || game.index != index) {
                return;
            }
            game.cancelPending();
            game.phase = Game.Phase.RESULT;
            QuestionRecord q = game.questions.get(index);
            for (Game.Player p : game.present()) {
                AnswerData answer = p.answers.get(index);
                double score = answer == null ? 0.0 : Grader.score(q, answer);
                GameScoring.Points points = GameScoring.points(score, p.streak);
                boolean correct = Grader.isCorrect(score);
                p.score += points.total();
                p.streak = points.streak();
                p.lastPoints = points.total();
                if (correct) {
                    p.correctCount++;
                } else {
                    p.wrong.add(q.id());
                }
                p.lastResult = new PlayerResult(answer != null, score, correct, points.total(), points.bonus(),
                        points.streak());
            }
            game.played = index + 1;
            boolean last = index == game.questions.size() - 1;
            List<LeaderboardEntry> board = leaderboard(game);
            List<Long> correctIds = Grader.correctIds(q).stream().sorted().toList();
            for (Game.Player p : game.present()) {
                p.session.send(Message.of(MessageType.QUESTION_RESULT, null, new QuestionResult(game.id, index,
                        game.questions.size(), correctIds, q.trueFalseAnswer(), q.explanation(), p.lastResult, board,
                        timing.resultPause().toMillis())));
            }
            game.pending = schedule(last ? () -> finish(game, GameEndReason.FINISHED, null, null)
                    : () -> showQuestion(game, index + 1), timing.resultPause());
        }
    }

    // ---------- 結束

    /** 房主提前結束；房主收到帶 requestId 的 GAME_OVER。 */
    public void end(Session session, String requestId) throws ProtocolException {
        synchronized (lock) {
            Room room = rooms.requireOwnedRoom(session, requestId);
            Game game = games.get(room.code());
            if (game == null) {
                throw new ProtocolException(ErrorCode.GAME_NOT_FOUND, "目前沒有進行中的競賽", requestId);
            }
            finish(game, GameEndReason.ENDED_BY_OWNER, session, requestId);
        }
    }

    private void finish(Game game, GameEndReason reason, Session requester, String requestId) {
        synchronized (lock) {
            if (!isActive(game)) {
                return;
            }
            stop(game);
            List<LeaderboardEntry> board = leaderboard(game);
            for (Game.Player p : game.present()) {
                p.session.send(Message.of(MessageType.GAME_OVER, p.session == requester ? requestId : null,
                        new GameOver(game.id, game.title, reason, game.questions.size(), game.played, board,
                                p.wrong.size())));
            }
            saveWrongs(game);
        }
    }

    /** 停止計時並把房間恢復為等待中；呼叫端須持有鎖。 */
    private void stop(Game game) {
        game.cancelPending();
        game.phase = Game.Phase.OVER;
        games.remove(game.room.code(), game);
        game.room.setPlaying(false);
    }

    /** 錯題本在鎖外寫入；包含中途離開的玩家已公布結果的題目。 */
    private void saveWrongs(Game game) {
        Instant now = clock.instant();
        for (Game.Player p : game.players.values()) {
            if (p.wrong.isEmpty()) {
                continue;
            }
            long userId = p.user.userId();
            List<Long> ids = List.copyOf(p.wrong);
            writer.execute(() -> {
                try {
                    repos.wrongs().add(userId, ids, now);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "競賽錯題寫入失敗（使用者 #" + userId + "）", e);
                }
            });
        }
    }

    // ---------- 房間成員變動（在鎖內呼叫）

    @Override
    public void playerLeft(Room room, Session leaver) {
        Game game = games.get(room.code());
        if (game == null) {
            return;
        }
        Game.Player p = game.players.get(leaver.user().userId());
        if (p != null) {
            p.present = false;
        }
        if (game.phase == Game.Phase.QUESTION) {
            broadcastProgress(game);
            if (game.allAnswered()) {
                closeQuestion(game, game.index);
            }
        }
    }

    @Override
    public void roomClosed(Room room) {
        Game game = games.get(room.code());
        if (game != null) {
            stop(game);
            saveWrongs(game);
        }
    }

    /** 測試與監控用：進行中的競賽數量。 */
    public int gameCount() {
        synchronized (lock) {
            return games.size();
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------- 輔助

    /** 同分同名次；同分時依顯示名稱排列。 */
    static List<LeaderboardEntry> leaderboard(Game game) {
        List<Game.Player> sorted = new ArrayList<>(game.present());
        sorted.sort(Comparator.comparingInt((Game.Player p) -> -p.score)
                .thenComparing(p -> p.user.displayName())
                .thenComparingLong(p -> p.user.userId()));
        List<LeaderboardEntry> board = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Game.Player p = sorted.get(i);
            int rank = i > 0 && sorted.get(i - 1).score == p.score ? board.get(i - 1).rank() : i + 1;
            board.add(new LeaderboardEntry(rank, p.user, p.score, p.correctCount, p.streak, p.lastPoints));
        }
        return List.copyOf(board);
    }

    private boolean isActive(Game game) {
        return game.phase != Game.Phase.OVER && games.get(game.room.code()) == game;
    }

    private void broadcastProgress(Game game) {
        List<Game.Player> present = game.present();
        int answered = (int) present.stream().filter(p -> p.answers.containsKey(game.index)).count();
        broadcast(game, Message.of(MessageType.GAME_PROGRESS, null,
                new GameProgress(game.id, game.index, answered, present.size())));
    }

    private static void broadcast(Game game, Message message) {
        game.present().forEach(p -> p.session.send(message));
    }

    private static void requireIdle(Room room, String requestId) throws ProtocolException {
        if (room.playing()) {
            throw new ProtocolException(ErrorCode.GAME_IN_PROGRESS, "競賽已經在進行中", requestId);
        }
    }

    private java.util.concurrent.ScheduledFuture<?> schedule(Runnable task, Duration delay) {
        return scheduler.schedule(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "競賽排程失敗", e);
            }
        }, delay.toNanos(), TimeUnit.NANOSECONDS);
    }

    private static ProtocolException invalid(String message, String requestId) {
        return new ProtocolException(ErrorCode.INVALID_PAYLOAD, message, requestId);
    }

    private static Thread daemon(Runnable r, String name) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        return t;
    }
}
