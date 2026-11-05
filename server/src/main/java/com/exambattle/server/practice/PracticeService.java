package com.exambattle.server.practice;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.SubjectInfo;
import com.exambattle.common.protocol.practice.AnswerData;
import com.exambattle.common.protocol.practice.AnswerRequest;
import com.exambattle.common.protocol.practice.AnswerResult;
import com.exambattle.common.protocol.practice.PracticeMode;
import com.exambattle.common.protocol.practice.PracticeOption;
import com.exambattle.common.protocol.practice.PracticeQuestion;
import com.exambattle.common.protocol.practice.PracticeResult;
import com.exambattle.common.protocol.practice.PracticeStarted;
import com.exambattle.common.protocol.practice.PracticeStats;
import com.exambattle.common.protocol.practice.RecordList;
import com.exambattle.common.protocol.practice.RecordSummary;
import com.exambattle.common.protocol.practice.ResultItem;
import com.exambattle.common.protocol.practice.RevealMode;
import com.exambattle.common.protocol.practice.StartPracticeRequest;
import com.exambattle.common.protocol.practice.SubmitRequest;
import com.exambattle.common.protocol.practice.WrongItem;
import com.exambattle.common.protocol.practice.WrongList;
import com.exambattle.server.dao.BankRecords.QuestionRecord;
import com.exambattle.server.dao.BankRecords.SubjectRecord;
import com.exambattle.server.dao.PracticeRecords.AnswerRow;
import com.exambattle.server.dao.PracticeRecords.NewAnswer;
import com.exambattle.server.dao.PracticeRecords.NewRecord;
import com.exambattle.server.dao.PracticeRecords.RecordDetail;
import com.exambattle.server.dao.PracticeRecords.RecordRow;
import com.exambattle.server.dao.PracticeRecords.StatsRow;
import com.exambattle.server.dao.PracticeRecords.WrongRow;
import com.exambattle.server.dao.Repositories;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 單人練習：選題、評分、交卷、紀錄、統計與錯題本。
 * 計分與計時一律由伺服器負責；交卷（或練習模式送出該題）前不回傳正確答案。
 * 每位使用者同時只有一個進行中的練習，開始新的會取代舊的。
 */
public final class PracticeService {

    private final Repositories repos;
    private final Clock clock;
    private final Random random;
    private final Map<Long, ActivePractice> active = new ConcurrentHashMap<>();

    public PracticeService(Repositories repos, Clock clock) {
        this(repos, clock, new SecureRandom());
    }

    PracticeService(Repositories repos, Clock clock, Random random) {
        this.repos = repos;
        this.clock = clock;
        this.random = random;
    }

    // ---------- 開始

    public PracticeStarted start(long userId, StartPracticeRequest request) throws PracticeException {
        if (request.mode() == null || request.revealMode() == null) {
            throw invalid("請選擇練習模式與答案揭曉方式");
        }
        Integer count = request.count();
        if (count != null && (count < 1 || count > Protocol.MAX_PRACTICE_COUNT)) {
            throw invalid("題數必須介於 1–" + Protocol.MAX_PRACTICE_COUNT);
        }

        String title;
        List<QuestionRecord> questions;
        Long sourceExamId = null;
        Long sourceSubjectId = null;
        switch (request.mode()) {
            case FULL_EXAM -> {
                if (request.examId() == null) {
                    throw invalid("請選擇考卷");
                }
                title = examTitle(request.examId(), userId);
                sourceExamId = request.examId();
                questions = repos.questions().findByExam(request.examId()).stream()
                        .filter(QuestionRecord::answerSet).toList();
            }
            case RANDOM -> {
                List<Long> ids = new ArrayList<>(repos.questions().answeredIds(request.examId(),
                        request.examId() == null ? request.subjectId() : null, userId));
                String scope;
                if (request.examId() != null) {
                    scope = examTitle(request.examId(), userId);
                    sourceExamId = request.examId();
                } else if (request.subjectId() != null) {
                    scope = subjectName(request.subjectId());
                    sourceSubjectId = request.subjectId();
                } else {
                    scope = "全部題庫";
                }
                title = "隨機抽題：" + scope;
                Collections.shuffle(ids, random);
                int limit = count == null ? Protocol.DEFAULT_PRACTICE_COUNT : count;
                questions = repos.questions().findByIds(ids.subList(0, Math.min(limit, ids.size())));
            }
            case WRONG_REVIEW -> {
                List<Long> ids = new ArrayList<>(repos.wrongs().list(userId, request.subjectId()).stream()
                        .map(WrongRow::questionId).toList());
                if (request.subjectId() != null) {
                    sourceSubjectId = request.subjectId();
                    title = "錯題複習：" + subjectName(request.subjectId());
                } else {
                    title = "錯題複習";
                }
                Collections.shuffle(ids, random);
                int limit = count == null ? Protocol.MAX_PRACTICE_COUNT : count;
                // 題目的答案可能在加入錯題本後被建立者清除，這類題目不出
                questions = repos.questions().findByIds(ids.subList(0, Math.min(limit, ids.size()))).stream()
                        .filter(QuestionRecord::answerSet).toList();
            }
            default -> throw new IllegalStateException("未處理的練習模式: " + request.mode());
        }
        if (questions.isEmpty()) {
            throw new PracticeException(ErrorCode.NO_QUESTIONS, request.mode() == PracticeMode.WRONG_REVIEW
                    ? "錯題本中沒有可以複習的題目"
                    : "沒有已設定答案的題目可以練習");
        }

        Instant now = now();
        ActivePractice practice = new ActivePractice(UUID.randomUUID().toString(), userId, request.mode(),
                request.revealMode(), title, sourceExamId, sourceSubjectId, now, questions);
        active.put(userId, practice);

        List<PracticeQuestion> shown = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            QuestionRecord q = questions.get(i);
            shown.add(new PracticeQuestion(i, q.id(), q.type(), q.content(),
                    q.options().stream().map(o -> new PracticeOption(o.id(), o.content(), o.imageId())).toList(),
                    q.groupId(), q.passage(), q.imageId(), q.passageImageId()));
        }
        return new PracticeStarted(practice.id, practice.mode, practice.revealMode, title, now.toString(), shown);
    }

    // ---------- 練習模式：逐題作答

    public AnswerResult answer(long userId, AnswerRequest request) throws PracticeException {
        ActivePractice practice = requireActive(userId, request.practiceId());
        if (practice.revealMode != RevealMode.IMMEDIATE) {
            throw new PracticeException(ErrorCode.WRONG_MODE, "考試模式請在交卷時一起送出答案");
        }
        int index = requireIndex(practice, request.index());
        QuestionRecord q = practice.questions.get(index);
        AnswerData answer = new AnswerData(index, request.selectedOptionIds(), request.trueFalseAnswer());
        double score = Grader.score(q, answer);
        if (!practice.record(index, new ActivePractice.Answered(answer, score, now()))) {
            throw new PracticeException(ErrorCode.ALREADY_ANSWERED, "第 " + (index + 1) + " 題已經作答過");
        }
        return new AnswerResult(index, Grader.isCorrect(score), score, List.copyOf(Grader.correctIds(q)),
                q.trueFalseAnswer(), q.explanation());
    }

    // ---------- 交卷

    public PracticeResult submit(long userId, SubmitRequest request) throws PracticeException {
        ActivePractice practice = requireActive(userId, request.practiceId());

        Map<Integer, ActivePractice.Answered> answers = new HashMap<>();
        if (practice.revealMode == RevealMode.IMMEDIATE) {
            answers.putAll(practice.answered());
        } else if (request.answers() != null) {
            for (AnswerData a : request.answers()) {
                if (a == null) {
                    continue;
                }
                int index = requireIndex(practice, a.index());
                answers.put(index, new ActivePractice.Answered(a, Grader.score(practice.questions.get(index), a), null));
            }
        }
        // 先完成所有檢查，再移除進行中的練習
        if (!active.remove(userId, practice)) {
            throw new PracticeException(ErrorCode.PRACTICE_NOT_FOUND, "這次練習已經結束");
        }

        Instant finishedAt = now();
        List<NewAnswer> rows = new ArrayList<>();
        List<Long> wrong = new ArrayList<>();
        List<Double> scores = new ArrayList<>();
        int correctCount = 0;
        for (int i = 0; i < practice.questions.size(); i++) {
            QuestionRecord q = practice.questions.get(i);
            ActivePractice.Answered given = answers.get(i);
            AnswerData answer = given == null ? new AnswerData(i, List.of(), null) : normalize(i, given.answer());
            double score = given == null ? 0.0 : given.score();
            boolean correct = Grader.isCorrect(score);
            scores.add(score);
            if (correct) {
                correctCount++;
            } else {
                wrong.add(q.id());
            }
            rows.add(new NewAnswer(i, q.id(), Snapshots.toJson(Snapshots.full(q)), Snapshots.toJson(answer), score,
                    correct, given == null ? null : given.answeredAt()));
        }
        NewRecord record = new NewRecord(userId, practice.mode, practice.revealMode, practice.title,
                practice.sourceExamId, practice.sourceSubjectId, practice.questions.size(), correctCount,
                Grader.total(scores), practice.startedAt, finishedAt, rows);
        long recordId = repos.practices().save(record, wrong);
        return result(repos.practices().find(recordId).orElseThrow());
    }

    /** 斷線時清除進行中的練習。 */
    public void discard(long userId) {
        active.remove(userId);
    }

    // ---------- 紀錄與統計

    public RecordList records(long userId) {
        List<RecordSummary> records = repos.practices().list(userId).stream().map(PracticeService::summary).toList();
        StatsRow stats = repos.practices().stats(userId);
        double accuracy = stats.questionCount() == 0 ? 0.0 : (double) stats.correctCount() / stats.questionCount();
        return new RecordList(records, new PracticeStats(stats.practiceCount(), stats.questionCount(),
                stats.correctCount(), accuracy, Math.round(stats.averageScore() * 10.0) / 10.0));
    }

    public PracticeResult record(long userId, long recordId) throws PracticeException {
        RecordDetail detail = repos.practices().find(recordId)
                .filter(d -> d.record().userId() == userId)
                .orElseThrow(() -> new PracticeException(ErrorCode.RECORD_NOT_FOUND, "找不到這筆紀錄"));
        return result(detail);
    }

    // ---------- 錯題本

    public WrongList wrongs(long userId, Long subjectId) {
        List<WrongRow> rows = repos.wrongs().list(userId, subjectId);
        Map<Long, QuestionRecord> questions = repos.questions()
                .findByIds(rows.stream().map(WrongRow::questionId).toList()).stream()
                .collect(Collectors.toMap(QuestionRecord::id, Function.identity()));
        List<WrongItem> items = new ArrayList<>();
        for (WrongRow row : rows) {
            QuestionRecord q = questions.get(row.questionId());
            if (q == null) {
                continue;
            }
            SubjectInfo subject = row.subjectId() == null ? null : new SubjectInfo(row.subjectId(), row.subjectName());
            items.add(new WrongItem(row.questionId(), subject, row.examTitle(), Snapshots.full(q), row.wrongCount(),
                    row.lastWrongAt().toString()));
        }
        return new WrongList(items);
    }

    public void removeWrong(long userId, long questionId) throws PracticeException {
        if (!repos.wrongs().remove(userId, questionId)) {
            throw new PracticeException(ErrorCode.QUESTION_NOT_FOUND, "錯題本中沒有這一題");
        }
    }

    // ---------- 輔助

    private ActivePractice requireActive(long userId, String practiceId) throws PracticeException {
        ActivePractice practice = active.get(userId);
        if (practice == null || !practice.id.equals(practiceId)) {
            throw new PracticeException(ErrorCode.PRACTICE_NOT_FOUND, "練習不存在或已經結束，請重新開始");
        }
        return practice;
    }

    private static int requireIndex(ActivePractice practice, Integer index) throws PracticeException {
        if (index == null || index < 0 || index >= practice.questions.size()) {
            throw invalid("題號錯誤");
        }
        return index;
    }

    /** 統一作答格式（index 修正、null 清單改為空清單），存進紀錄。 */
    private static AnswerData normalize(int index, AnswerData a) {
        List<Long> selected = a.selectedOptionIds() == null ? List.of() : a.selectedOptionIds();
        return new AnswerData(index, selected, a.trueFalseAnswer());
    }

    /** 考卷名稱；看不到的私人考卷視同不存在（D-039）。 */
    private String examTitle(long examId, long userId) throws PracticeException {
        return repos.exams().findById(examId)
                .filter(s -> s.exam().visibleTo(userId))
                .map(s -> s.exam().title())
                .orElseThrow(() -> new PracticeException(ErrorCode.EXAM_NOT_FOUND, "找不到考卷 #" + examId));
    }

    private String subjectName(long subjectId) throws PracticeException {
        return repos.subjects().findById(subjectId)
                .map(SubjectRecord::name)
                .orElseThrow(() -> new PracticeException(ErrorCode.SUBJECT_NOT_FOUND, "找不到分類 #" + subjectId));
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    private static PracticeException invalid(String message) {
        return new PracticeException(ErrorCode.INVALID_PAYLOAD, message);
    }

    private static RecordSummary summary(RecordRow r) {
        return new RecordSummary(r.id(), r.mode(), r.revealMode(), r.title(), r.startedAt().toString(),
                r.finishedAt().toString(), r.durationMs(), r.questionCount(), r.correctCount(), r.score());
    }

    private static PracticeResult result(RecordDetail detail) {
        List<ResultItem> items = new ArrayList<>();
        for (AnswerRow a : detail.answers()) {
            QuestionData snapshot = Snapshots.question(a.snapshotJson());
            items.add(new ResultItem(a.position(), snapshot, Snapshots.answer(a.answerJson()), a.score(), a.correct(),
                    a.answeredAt() == null ? null : a.answeredAt().toString()));
        }
        return new PracticeResult(summary(detail.record()), items);
    }
}
