package com.exambattle.common.protocol;

import java.util.Optional;

/**
 * 協定中所有的訊息類型，對應 docs/PROTOCOL.md 第 3 節。
 */
public enum MessageType {
    // 連線
    HELLO(true),
    WELCOME(false),
    PING(true),
    PONG(true),
    BYE(true),
    ERROR(false),

    // 帳號
    REGISTER(true),
    REGISTER_OK(false),
    LOGIN(true),
    LOGIN_OK(false),
    CLIENT_JOINED(false),
    CLIENT_LEFT(false),

    // 房間
    CREATE_ROOM(true),
    JOIN_ROOM(true),
    LEAVE_ROOM(true),
    GET_ROOM(true),
    ROOM_STATE(false),
    LEFT_ROOM(false),
    ROOM_PLAYER_JOINED(false),
    ROOM_PLAYER_LEFT(false),
    ROOM_CLOSED(false),

    // 聊天
    CHAT(true),

    // 題庫（第三階段）
    LIST_SUBJECTS(true),
    SUBJECTS(false),
    CREATE_SUBJECT(true),
    SUBJECT_SAVED(false),
    LIST_EXAMS(true),
    EXAMS(false),
    GET_EXAM(true),
    EXAM_DETAIL(false),
    CREATE_EXAM(true),
    UPDATE_EXAM(true),
    EXAM_SAVED(false),
    DELETE_EXAM(true),
    EXAM_DELETED(false),
    ADD_QUESTION(true),
    UPDATE_QUESTION(true),
    QUESTION_SAVED(false),
    DELETE_QUESTION(true),
    QUESTION_DELETED(false),
    REORDER_QUESTIONS(true),
    IMPORT_QUESTIONS(true),
    IMPORT_DONE(false),
    UPDATE_GROUP(true),
    GROUP_SAVED(false),
    TAKE_DOWN_EXAM(true),
    UPLOAD_IMAGE(true),
    IMAGE_SAVED(false),
    GET_IMAGE(true),
    IMAGE(false),

    // 單人練習、紀錄、錯題本（第五階段）
    START_PRACTICE(true),
    PRACTICE_STARTED(false),
    ANSWER_QUESTION(true),
    ANSWER_RESULT(false),
    SUBMIT_PRACTICE(true),
    PRACTICE_RESULT(false),
    LIST_RECORDS(true),
    RECORDS(false),
    GET_RECORD(true),
    LIST_WRONG(true),
    WRONG_LIST(false),
    REMOVE_WRONG(true),
    WRONG_REMOVED(false),

    // 多人競賽（第四階段）
    START_GAME(true),
    GAME_STARTED(false),
    GAME_QUESTION(false),
    SUBMIT_ANSWER(true),
    ANSWER_ACCEPTED(false),
    GAME_PROGRESS(false),
    QUESTION_RESULT(false),
    GAME_OVER(false),
    END_GAME(true),
    KICK_PLAYER(true),
    KICKED(false);

    private final boolean clientSendable;

    MessageType(boolean clientSendable) {
        this.clientSendable = clientSendable;
    }

    /** 客戶端是否可以送出此類型；伺服器專用類型由客戶端送出時視為 UNKNOWN_TYPE。 */
    public boolean isClientSendable() {
        return clientSendable;
    }

    /** 將線路上的字串轉成訊息類型；不認得時回傳 empty。 */
    public static Optional<MessageType> fromWire(String type) {
        if (type == null) {
            return Optional.empty();
        }
        for (MessageType t : values()) {
            if (t.name().equals(type)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
