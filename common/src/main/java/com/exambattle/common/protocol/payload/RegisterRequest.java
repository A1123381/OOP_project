package com.exambattle.common.protocol.payload;

/** REGISTER：建立帳號。 */
public record RegisterRequest(String username, String password, String displayName) {

    /** 避免密碼出現在日誌或除錯輸出。 */
    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", displayName=" + displayName + "]";
    }
}
