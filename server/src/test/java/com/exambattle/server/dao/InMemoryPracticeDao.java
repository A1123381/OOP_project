package com.exambattle.server.dao;

import com.exambattle.server.dao.BankRecords.ExamRecord;
import com.exambattle.server.dao.PracticeRecords.AnswerRow;
import com.exambattle.server.dao.PracticeRecords.NewAnswer;
import com.exambattle.server.dao.PracticeRecords.NewRecord;
import com.exambattle.server.dao.PracticeRecords.RecordDetail;
import com.exambattle.server.dao.PracticeRecords.RecordRow;
import com.exambattle.server.dao.PracticeRecords.StatsRow;
import com.exambattle.server.dao.PracticeRecords.WrongRow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 測試用：以記憶體實作練習紀錄與錯題本，行為與 JdbcPracticeDao 一致
 * （已刪除的題目不加入錯題本、作答的 questionId 變成 null、錯題累計次數）。
 */
public final class InMemoryPracticeDao implements PracticeDao, WrongQuestionDao {

    private record Wrong(long questionId, int count, Instant first, Instant last) {
    }

    private final InMemoryBankDao bank;
    private final Map<Long, RecordDetail> records = new LinkedHashMap<>();
    private final Map<Long, Map<Long, Wrong>> wrongs = new LinkedHashMap<>();
    private long nextId = 1;

    InMemoryPracticeDao(InMemoryBankDao bank) {
        this.bank = bank;
    }

    @Override
    public synchronized long save(NewRecord r, List<Long> wrongQuestionIds) {
        long id = nextId++;
        RecordRow row = new RecordRow(id, r.userId(), r.mode(), r.revealMode(), r.title(), r.questionCount(),
                r.correctCount(), r.score(), r.startedAt(), r.finishedAt(),
                r.finishedAt().toEpochMilli() - r.startedAt().toEpochMilli());
        List<AnswerRow> answers = new ArrayList<>();
        for (NewAnswer a : r.answers()) {
            Long questionId = bank.questionExists(a.questionId()) ? a.questionId() : null;
            answers.add(new AnswerRow(a.position(), questionId, a.snapshotJson(), a.answerJson(), a.score(),
                    a.correct(), a.answeredAt()));
        }
        records.put(id, new RecordDetail(row, List.copyOf(answers)));

        Map<Long, Wrong> book = wrongs.computeIfAbsent(r.userId(), k -> new LinkedHashMap<>());
        for (long questionId : wrongQuestionIds) {
            if (!bank.questionExists(questionId)) {
                continue;
            }
            Wrong old = book.get(questionId);
            book.put(questionId, old == null
                    ? new Wrong(questionId, 1, r.finishedAt(), r.finishedAt())
                    : new Wrong(questionId, old.count() + 1, old.first(), r.finishedAt()));
        }
        return id;
    }

    @Override
    public synchronized List<RecordRow> list(long userId) {
        return records.values().stream()
                .map(RecordDetail::record)
                .filter(r -> r.userId() == userId)
                .sorted(Comparator.comparing(RecordRow::finishedAt).thenComparing(RecordRow::id).reversed())
                .toList();
    }

    @Override
    public synchronized Optional<RecordDetail> find(long recordId) {
        return Optional.ofNullable(records.get(recordId));
    }

    @Override
    public synchronized StatsRow stats(long userId) {
        List<RecordRow> mine = list(userId);
        int questions = mine.stream().mapToInt(RecordRow::questionCount).sum();
        int correct = mine.stream().mapToInt(RecordRow::correctCount).sum();
        double average = mine.stream().mapToDouble(RecordRow::score).average().orElse(0.0);
        return new StatsRow(mine.size(), questions, correct, average);
    }

    @Override
    public synchronized List<WrongRow> list(long userId, Long subjectId) {
        List<WrongRow> rows = new ArrayList<>();
        for (Wrong w : wrongs.getOrDefault(userId, Map.of()).values()) {
            if (!bank.questionExists(w.questionId())) {
                continue; // 對應 ON DELETE CASCADE
            }
            Optional<ExamRecord> exam = bank.firstExamOf(w.questionId());
            Long sid = exam.map(ExamRecord::subjectId).orElse(null);
            if (subjectId != null && !subjectId.equals(sid)) {
                continue;
            }
            String subjectName = sid == null ? null : bank.subject(sid).map(s -> s.name()).orElse(null);
            rows.add(new WrongRow(w.questionId(), w.count(), w.last(), exam.map(ExamRecord::title).orElse(null),
                    sid, subjectName));
        }
        rows.sort(Comparator.comparing(WrongRow::lastWrongAt).thenComparing(WrongRow::questionId).reversed());
        return rows;
    }

    @Override
    public synchronized boolean remove(long userId, long questionId) {
        return wrongs.getOrDefault(userId, new LinkedHashMap<>()).remove(questionId) != null;
    }
}
