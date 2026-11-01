package com.exambattle.server.handler;

import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.ProtocolException;
import com.exambattle.common.protocol.practice.AnswerRequest;
import com.exambattle.common.protocol.practice.ListWrongRequest;
import com.exambattle.common.protocol.practice.RecordRef;
import com.exambattle.common.protocol.practice.StartPracticeRequest;
import com.exambattle.common.protocol.practice.SubmitRequest;
import com.exambattle.common.protocol.practice.WrongRef;
import com.exambattle.server.Session;
import com.exambattle.server.practice.PracticeException;
import com.exambattle.server.practice.PracticeService;

/**
 * 處理單人練習、紀錄與錯題本訊息。呼叫端須保證已登入。
 * 和題庫一樣只碰資料庫與使用者自己的練習，不持有房間的共用鎖。
 */
public final class PracticeHandler {

    private final PracticeService practices;

    public PracticeHandler(PracticeService practices) {
        this.practices = practices;
    }

    public void handle(Session session, MessageType type, Message message) throws ProtocolException {
        String requestId = message.requestId();
        long userId = session.user().userId();
        try {
            Message reply = switch (type) {
                case START_PRACTICE -> Message.of(MessageType.PRACTICE_STARTED, requestId,
                        practices.start(userId, message.payloadAs(StartPracticeRequest.class)));
                case ANSWER_QUESTION -> Message.of(MessageType.ANSWER_RESULT, requestId,
                        practices.answer(userId, message.payloadAs(AnswerRequest.class)));
                case SUBMIT_PRACTICE -> Message.of(MessageType.PRACTICE_RESULT, requestId,
                        practices.submit(userId, message.payloadAs(SubmitRequest.class)));
                case LIST_RECORDS -> Message.of(MessageType.RECORDS, requestId, practices.records(userId));
                case GET_RECORD -> {
                    Long recordId = message.payloadAs(RecordRef.class).recordId();
                    if (recordId == null) {
                        throw PayloadValidator.invalid("缺少 recordId", requestId);
                    }
                    yield Message.of(MessageType.PRACTICE_RESULT, requestId, practices.record(userId, recordId));
                }
                case LIST_WRONG -> Message.of(MessageType.WRONG_LIST, requestId,
                        practices.wrongs(userId, message.payloadAs(ListWrongRequest.class).subjectId()));
                case REMOVE_WRONG -> {
                    Long questionId = message.payloadAs(WrongRef.class).questionId();
                    if (questionId == null) {
                        throw PayloadValidator.invalid("缺少 questionId", requestId);
                    }
                    practices.removeWrong(userId, questionId);
                    yield Message.of(MessageType.WRONG_REMOVED, requestId, new WrongRef(questionId));
                }
                default -> throw new IllegalArgumentException("不是練習訊息: " + type);
            };
            session.send(reply);
        } catch (PracticeException e) {
            throw new ProtocolException(e.code(), e.getMessage(), requestId);
        }
    }

    /** 連線結束時清除使用者進行中的練習。 */
    public void discard(long userId) {
        practices.discard(userId);
    }
}
