package com.exambattle.server.dao;

/** 資料庫操作失敗。呼叫端通常回應 INTERNAL 錯誤。 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
