package com.exambattle.common.protocol.practice;

import java.util.List;

/** PRACTICE_STARTED：題目依作答順序排列；startedAt 為伺服器時間（ISO-8601 UTC）。 */
public record PracticeStarted(String practiceId, PracticeMode mode, RevealMode revealMode, String title,
                              String startedAt, List<PracticeQuestion> questions) {
}
