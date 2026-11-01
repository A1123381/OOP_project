package com.exambattle.common.protocol.payload;

/** LOGIN：以帳號密碼登入。 */
public record LoginRequest(String username, String password) {

    /** 避免密碼出現在日誌或除錯輸出。 */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + "]";
    }
}
