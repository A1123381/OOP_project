package com.exambattle.server.bank;

import com.exambattle.common.protocol.ErrorCode;

/** 題庫操作失敗（找不到、權限不足、欄位錯誤）；由 BankHandler 轉成 ERROR 回應。 */
public class BankException extends Exception {

    private final ErrorCode code;

    public BankException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    static BankException invalid(String message) {
        return new BankException(ErrorCode.INVALID_PAYLOAD, message);
    }
}
