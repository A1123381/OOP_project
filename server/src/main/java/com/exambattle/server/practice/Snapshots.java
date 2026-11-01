package com.exambattle.server.practice;

import com.exambattle.common.protocol.bank.OptionData;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.practice.AnswerData;
import com.exambattle.server.dao.BankRecords.QuestionRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 紀錄中的題目快照與作答，以 JSON 存進 answer_records（見 DECISIONS.md D-025），
 * 題目之後被修改或刪除時，歷史紀錄仍保持作答當時的內容與正確答案。
 */
final class Snapshots {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private Snapshots() {
    }

    /** 題目的完整內容（含正確答案與解說）。 */
    static QuestionData full(QuestionRecord q) {
        return new QuestionData(q.id(), q.type(), q.content(), q.explanation(),
                q.options().stream().map(o -> new OptionData(o.id(), o.content(), o.correct())).toList(),
                q.trueFalseAnswer(), q.answerSet());
    }

    static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("無法序列化紀錄", e);
        }
    }

    static QuestionData question(String json) {
        return read(json, QuestionData.class);
    }

    static AnswerData answer(String json) {
        return read(json, AnswerData.class);
    }

    private static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("紀錄資料格式錯誤", e);
        }
    }
}
