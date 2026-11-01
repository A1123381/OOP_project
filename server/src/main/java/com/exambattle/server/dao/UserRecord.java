package com.exambattle.server.dao;

/**
 * users 資料表的一列。密碼只以雜湊、salt、迭代次數保存。
 *
 * <p>含 byte[] 欄位，record 的 equals 以參考比較；比對密碼請使用 PasswordHasher。
 */
public record UserRecord(long id, String username, String displayName,
                         byte[] passwordHash, byte[] passwordSalt, int passwordIterations) {

    /** 避免雜湊值出現在日誌中。 */
    @Override
    public String toString() {
        return "UserRecord[id=" + id + ", username=" + username + ", displayName=" + displayName + "]";
    }
}
