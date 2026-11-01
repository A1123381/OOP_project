package com.exambattle.common.protocol;

/** ERROR 訊息的錯誤碼，對應 docs/PROTOCOL.md 的錯誤碼表。 */
public enum ErrorCode {
    // 協定
    BAD_JSON,
    UNKNOWN_TYPE,
    NOT_HELLO,
    ALREADY_WELCOMED,
    INVALID_PAYLOAD,
    LINE_TOO_LONG,
    INTERNAL,

    // 帳號
    NOT_LOGGED_IN,
    ALREADY_LOGGED_IN,
    INVALID_CREDENTIALS,
    USERNAME_TAKEN,

    // 房間
    ROOM_NOT_FOUND,
    ROOM_FULL,
    ALREADY_IN_ROOM,
    NOT_IN_ROOM,

    // 題庫
    SUBJECT_NOT_FOUND,
    SUBJECT_EXISTS,
    EXAM_NOT_FOUND,
    QUESTION_NOT_FOUND,
    GROUP_NOT_FOUND,
    NOT_OWNER,
    TOO_MANY_QUESTIONS,

    // 練習
    NO_QUESTIONS,
    PRACTICE_NOT_FOUND,
    ALREADY_ANSWERED,
    WRONG_MODE,
    RECORD_NOT_FOUND
}
