package com.exambattle.client.gui.model;

import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.practice.AnswerData;
import com.exambattle.common.protocol.practice.AnswerResult;
import com.exambattle.common.protocol.practice.PracticeMode;
import com.exambattle.common.protocol.practice.PracticeOption;
import com.exambattle.common.protocol.practice.PracticeQuestion;
import com.exambattle.common.protocol.practice.PracticeStarted;
import com.exambattle.common.protocol.practice.RevealMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PracticeSessionTest {

    private static PracticeStarted started(RevealMode reveal) {
        List<PracticeOption> options = List.of(new PracticeOption(11, "A"), new PracticeOption(12, "B"),
                new PracticeOption(13, "C"));
        return new PracticeStarted("p1", PracticeMode.FULL_EXAM, reveal, "期中考", "2026-11-01T04:00:00Z", List.of(
                new PracticeQuestion(0, 1, QuestionType.SINGLE_CHOICE, "單選", options),
                new PracticeQuestion(1, 2, QuestionType.MULTIPLE_CHOICE, "多選", options),
                new PracticeQuestion(2, 3, QuestionType.TRUE_FALSE, "是非", List.of())));
    }

    @Test
    void navigationStaysInRange() {
        PracticeSession s = new PracticeSession(started(RevealMode.AFTER_SUBMIT));

        assertFalse(s.hasPrevious());
        s.goTo(2);
        assertFalse(s.hasNext());
        s.goTo(5);
        assertEquals(2, s.current());
        s.goTo(-1);
        assertEquals(2, s.current());
    }

    @Test
    void answersAreTrackedAndOnlyAnsweredOnesSubmitted() {
        PracticeSession s = new PracticeSession(started(RevealMode.AFTER_SUBMIT));

        s.setChoices(1, Set.of(11L, 13L));
        s.setTrueFalse(2, false);
        assertEquals(1, s.unansweredCount());

        List<AnswerData> answers = s.answersToSubmit();
        assertEquals(List.of(1, 2), answers.stream().map(AnswerData::index).toList());
        assertEquals(Set.of(11L, 13L), Set.copyOf(answers.get(0).selectedOptionIds()));
        assertEquals(Boolean.FALSE, answers.get(1).trueFalseAnswer());

        s.setChoices(1, Set.of());
        s.setTrueFalse(2, null);
        assertEquals(3, s.unansweredCount(), "清除選擇後回到未作答");
        assertNull(s.answer(1));
    }

    @Test
    void singleChoiceAcceptsOneOption() {
        PracticeSession s = new PracticeSession(started(RevealMode.AFTER_SUBMIT));

        assertThrows(IllegalArgumentException.class, () -> s.setChoices(0, Set.of(11L, 12L)));
    }

    @Test
    void immediateModeLocksGradedQuestions() {
        PracticeSession s = new PracticeSession(started(RevealMode.IMMEDIATE));
        s.setChoices(0, Set.of(12L));

        s.recordResult(new AnswerResult(0, false, 0.0, List.of(11L), null, "解說"));
        s.setChoices(0, Set.of(11L));

        assertTrue(s.immediate());
        assertTrue(s.locked(0));
        assertEquals(Set.of(12L), s.choices(0), "已批改的題目不能修改");
        assertFalse(s.result(0).correct());
    }
}
