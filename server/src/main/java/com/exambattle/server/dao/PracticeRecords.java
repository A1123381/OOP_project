package com.exambattle.server.dao;

import com.exambattle.common.protocol.practice.PracticeMode;
import com.exambattle.common.protocol.practice.RevealMode;

import java.time.Instant;
import java.util.List;

/** 練習紀錄與錯題本資料表對應的資料型別。 */
public final class PracticeRecords {

    private PracticeRecords() {
    }

    /** 要寫入的一題作答。questionId 為題目目前的 id；snapshot、answer 為 JSON。 */
    public record NewAnswer(int position, long questionId, String snapshotJson, String answerJson, double score,
                            boolean correct, Instant answeredAt) {
    }

    /** 要寫入的一次練習（交卷時）。 */
    public record NewRecord(long userId, PracticeMode mode, RevealMode revealMode, String title, Long sourceExamId,
                            Long sourceSubjectId, int questionCount, int correctCount, double score,
                            Instant startedAt, Instant finishedAt, List<NewAnswer> answers) {
    }

    /** practice_records 的一列。 */
    public record RecordRow(long id, long userId, PracticeMode mode, RevealMode revealMode, String title,
                            int questionCount, int correctCount, double score, Instant startedAt,
                            Instant finishedAt, long durationMs) {
    }

    /** answer_records 的一列。questionId 在題目被刪除後為 null。 */
    public record AnswerRow(int position, Long questionId, String snapshotJson, String answerJson, double score,
                            boolean correct, Instant answeredAt) {
    }

    /** 一筆紀錄與其作答（依 position 排序）。 */
    public record RecordDetail(RecordRow record, List<AnswerRow> answers) {
    }

    /** 個人統計。averageScore 為各次分數的平均；沒有紀錄時全部為 0。 */
    public record StatsRow(int practiceCount, int questionCount, int correctCount, double averageScore) {
    }

    /** 錯題本的一列，附上題目所屬（第一份）考卷與分類；題目不在任何考卷時為 null。 */
    public record WrongRow(long questionId, int wrongCount, Instant lastWrongAt, String examTitle,
                           Long subjectId, String subjectName) {
    }
}
