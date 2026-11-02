package com.exambattle.client.gui.model;

import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.game.PlayerResult;
import com.exambattle.common.protocol.practice.PracticeOption;
import com.exambattle.common.protocol.practice.PracticeQuestion;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameTextTest {

    @Test
    void verdictShowsPointsAndStreakBonus() {
        assertEquals("✔ 答對！+1100（連續答對 2 題，加成 +100）", GameText.verdict(new PlayerResult(true, 1.0, true, 1100, 100, 2)));
        assertEquals("✔ 答對！+1000", GameText.verdict(new PlayerResult(true, 1.0, true, 1000, 0, 1)));
        assertEquals("△ 部分給分　+600", GameText.verdict(new PlayerResult(true, 0.6, false, 600, 0, 0)));
        assertEquals("✘ 答錯　+0", GameText.verdict(new PlayerResult(true, 0.0, false, 0, 0, 0)));
        assertEquals("⏱ 沒有作答　+0", GameText.verdict(new PlayerResult(false, 0.0, false, 0, 0, 0)));
    }

    @Test
    void correctAnswerUsesLettersOrTrueFalse() {
        PracticeQuestion multi = new PracticeQuestion(0, 1, QuestionType.MULTIPLE_CHOICE, "質數",
                List.of(new PracticeOption(11, "2"), new PracticeOption(12, "3"), new PracticeOption(13, "4")));
        assertEquals("A、B", GameText.correctAnswer(multi, List.of(12L, 11L), null));
        PracticeQuestion tf = new PracticeQuestion(0, 2, QuestionType.TRUE_FALSE, "是非", List.of());
        assertEquals("錯", GameText.correctAnswer(tf, List.of(), false));
    }

    @Test
    void countdownRoundsUp() {
        assertEquals(10, GameText.secondsLeft(9_001));
        assertEquals(1, GameText.secondsLeft(1));
        assertEquals(0, GameText.secondsLeft(-50));
    }
}
