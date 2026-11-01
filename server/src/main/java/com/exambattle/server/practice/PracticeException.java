package com.exambattle.server.practice;

import com.exambattle.common.protocol.ErrorCode;

/** 練習操作失敗；由 PracticeHandler 轉成 ERROR 回應。 */
public class PracticeException extends Exception {

    private final ErrorCode code;

    public PracticeException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
