package com.exambattle.common.protocol.practice;

/**
 * START_PRACTICE。
 * <ul>
 *   <li>FULL_EXAM：examId 必填</li>
 *   <li>RANDOM：examId 或 subjectId 擇一，皆為 null 表示全部題庫；count 為題數</li>
 *   <li>WRONG_REVIEW：subjectId 選填；count 為最多題數</li>
 * </ul>
 * count 為 null 時：RANDOM 預設 10 題，WRONG_REVIEW 為全部（上限 100）。
 */
public record StartPracticeRequest(PracticeMode mode, RevealMode revealMode, Long examId, Long subjectId,
                                   Integer count) {
}
