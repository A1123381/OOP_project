package com.exambattle.server.practice;

import com.exambattle.common.protocol.practice.AnswerData;
import com.exambattle.common.protocol.practice.PracticeMode;
import com.exambattle.common.protocol.practice.RevealMode;
import com.exambattle.server.dao.BankRecords.QuestionRecord;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 進行中的練習，只存在伺服器記憶體（見 DECISIONS.md D-025）。
 * 題目與正確答案在開始時固定，客戶端交卷前拿不到答案。練習模式的逐題作答也記在這裡。
 */
final class ActivePractice {

    /** 練習模式中已送出的一題。 */
    record Answered(AnswerData answer, double score, Instant answeredAt) {
    }

    final String id;
    final long userId;
    final PracticeMode mode;
    final RevealMode revealMode;
    final String title;
    final Long sourceExamId;
    final Long sourceSubjectId;
    final Instant startedAt;
    final List<QuestionRecord> questions;
    private final Map<Integer, Answered> answered = new HashMap<>();

    ActivePractice(String id, long userId, PracticeMode mode, RevealMode revealMode, String title, Long sourceExamId,
                   Long sourceSubjectId, Instant startedAt, List<QuestionRecord> questions) {
        this.id = id;
        this.userId = userId;
        this.mode = mode;
        this.revealMode = revealMode;
        this.title = title;
        this.sourceExamId = sourceExamId;
        this.sourceSubjectId = sourceSubjectId;
        this.startedAt = startedAt;
        this.questions = List.copyOf(questions);
    }

    /** 記錄練習模式的作答；已作答過回傳 false。 */
    synchronized boolean record(int index, Answered answer) {
        return answered.putIfAbsent(index, answer) == null;
    }

    synchronized Map<Integer, Answered> answered() {
        return Map.copyOf(answered);
    }
}
