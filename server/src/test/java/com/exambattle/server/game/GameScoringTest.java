package com.exambattle.server.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameScoringTest {

    @Test
    void correctAnswersBuildAStreakBonusUpTo500() {
        assertEquals(new GameScoring.Points(1000, 0, 1), GameScoring.points(1.0, 0));
        assertEquals(new GameScoring.Points(1000, 100, 2), GameScoring.points(1.0, 1));
        assertEquals(new GameScoring.Points(1000, 500, 6), GameScoring.points(1.0, 5));
        assertEquals(new GameScoring.Points(1000, 500, 11), GameScoring.points(1.0, 10), "加成上限 500");
    }

    @Test
    void wrongOrPartialResetsTheStreak() {
        assertEquals(new GameScoring.Points(0, 0, 0), GameScoring.points(0.0, 4));
        assertEquals(new GameScoring.Points(600, 0, 0), GameScoring.points(0.6, 4), "多選部分給分不算答對");
        assertEquals(1500, GameScoring.points(1.0, 7).total());
    }
}
