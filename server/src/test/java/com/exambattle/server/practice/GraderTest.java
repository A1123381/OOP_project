package com.exambattle.server.practice;

import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.practice.AnswerData;
import com.exambattle.server.dao.BankRecords.OptionRecord;
import com.exambattle.server.dao.BankRecords.QuestionRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraderTest {

    /** 選項 id 為 1..n；correct 為正確選項的 id。 */
    private static QuestionRecord choice(QuestionType type, int n, long... correct) {
        List<OptionRecord> options = new ArrayList<>();
        for (long id = 1; id <= n; id++) {
            long current = id;
            boolean isCorrect = java.util.Arrays.stream(correct).anyMatch(c -> c == current);
            options.add(new OptionRecord(id, "選項" + id, isCorrect));
        }
        return new QuestionRecord(99, type, "題目", null, null, 1, options);
    }

    private static AnswerData pick(Long... ids) {
        return new AnswerData(0, List.of(ids), null);
    }

    @Test
    void singleChoice() {
        QuestionRecord q = choice(QuestionType.SINGLE_CHOICE, 4, 2);

        assertEquals(1.0, Grader.score(q, pick(2L)));
        assertEquals(0.0, Grader.score(q, pick(3L)));
        assertEquals(0.0, Grader.score(q, pick(2L, 3L)), "單選題選兩個不給分");
        assertEquals(0.0, Grader.score(q, pick()));
        assertEquals(0.0, Grader.score(q, null));
    }

    @Test
    void trueFalse() {
        QuestionRecord q = new QuestionRecord(1, QuestionType.TRUE_FALSE, "是非", null, false, 1, List.of());

        assertEquals(1.0, Grader.score(q, new AnswerData(0, null, false)));
        assertEquals(0.0, Grader.score(q, new AnswerData(0, null, true)));
        assertEquals(0.0, Grader.score(q, new AnswerData(0, null, null)), "未作答");
    }

    @Test
    void multipleChoiceWithFiveOptionsFollowsGsatRule() {
        QuestionRecord q = choice(QuestionType.MULTIPLE_CHOICE, 5, 1, 2); // 正確 A、B

        assertEquals(1.0, Grader.score(q, pick(1L, 2L)), "k=0 全對");
        assertEquals(0.6, Grader.score(q, pick(1L)), 1e-9, "k=1 少選 B");
        assertEquals(0.6, Grader.score(q, pick(1L, 2L, 3L)), 1e-9, "k=1 多選 C");
        assertEquals(0.2, Grader.score(q, pick(1L, 3L)), 1e-9, "k=2");
        assertEquals(0.0, Grader.score(q, pick(3L, 4L)), "k=4");
        assertEquals(0.0, Grader.score(q, pick()), "未作答 0 分");
    }

    @Test
    void multipleChoiceWithFourOptions() {
        QuestionRecord q = choice(QuestionType.MULTIPLE_CHOICE, 4, 1, 2, 3);

        assertEquals(0.5, Grader.score(q, pick(1L, 2L)), 1e-9, "k=1 → (4−2)/4");
        assertEquals(0.0, Grader.score(q, pick(1L)), "k=2 → 0");
        assertEquals(0.0, Grader.score(q, pick(4L)), "k=4 → 不得為負");
    }

    @Test
    void unknownOptionIdsAreIgnored() {
        QuestionRecord q = choice(QuestionType.MULTIPLE_CHOICE, 5, 1);

        assertEquals(1.0, Grader.score(q, pick(1L, 999L)));
    }

    @Test
    void onlyFullScoreCountsAsCorrect() {
        assertTrue(Grader.isCorrect(1.0));
        assertFalse(Grader.isCorrect(0.6));
    }

    @Test
    void totalIsHundredPointScaleRoundedToOneDecimal() {
        assertEquals(53.3, Grader.total(List.of(1.0, 0.6, 0.0)));
        assertEquals(100.0, Grader.total(List.of(1.0, 1.0)));
        assertEquals(0.0, Grader.total(List.of()));
        assertEquals(66.7, Grader.total(List.of(1.0, 1.0, 0.0)));
    }
}
