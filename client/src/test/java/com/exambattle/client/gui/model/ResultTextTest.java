package com.exambattle.client.gui.model;

import com.exambattle.common.protocol.bank.OptionData;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.practice.AnswerData;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultTextTest {

    private static final QuestionData MULTI = new QuestionData(1L, QuestionType.MULTIPLE_CHOICE, "多選", null,
            List.of(new OptionData(11L, "2", true), new OptionData(12L, "3", true), new OptionData(13L, "4", false)),
            null, true);

    @Test
    void answersAsLetters() {
        assertEquals("A、B", ResultText.correctAnswer(MULTI));
        assertEquals("A、C", ResultText.yourAnswer(MULTI, new AnswerData(0, List.of(13L, 11L), null)));
        assertEquals("未作答", ResultText.yourAnswer(MULTI, new AnswerData(0, List.of(), null)));
        assertEquals("未作答", ResultText.yourAnswer(MULTI, null));
    }

    @Test
    void trueFalseAnswers() {
        QuestionData tf = new QuestionData(2L, QuestionType.TRUE_FALSE, "是非", null, List.of(), false, true);

        assertEquals("錯", ResultText.correctAnswer(tf));
        assertEquals("對", ResultText.yourAnswer(tf, new AnswerData(0, null, true)));
        assertEquals("未作答", ResultText.yourAnswer(tf, new AnswerData(0, null, null)));
    }

    @Test
    void numbers() {
        assertEquals("1", ResultText.questionScore(1.0));
        assertEquals("0.6", ResultText.questionScore(0.6));
        assertEquals("0", ResultText.questionScore(0.0));
        assertEquals("53.3", ResultText.score(53.3));
        assertEquals("100", ResultText.score(100.0));
        assertEquals("33.3%", ResultText.percent(1.0 / 3));
        assertEquals("100%", ResultText.percent(1.0));
    }

    @Test
    void durations() {
        assertEquals("45 秒", ResultText.duration(45_900));
        assertEquals("3 分 05 秒", ResultText.duration(185_000));
        assertEquals("1 小時 02 分", ResultText.duration(3_720_000));
        assertEquals("0 秒", ResultText.duration(-5));
    }

    @Test
    void localDateTime() {
        assertEquals("2026-11-01 12:05", ResultText.dateTime("2026-11-01T04:05:06.789Z", ZoneId.of("Asia/Taipei")));
        assertEquals("", ResultText.dateTime("not a time", ZoneId.of("Asia/Taipei")));
    }
}
