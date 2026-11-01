package com.exambattle.server.dao;

/** 要新增到 users 資料表的帳號（尚未有 id）。 */
public record NewUser(String username, String displayName,
                      byte[] passwordHash, byte[] passwordSalt, int passwordIterations) {

    @Override
    public String toString() {
        return "NewUser[username=" + username + ", displayName=" + displayName + "]";
    }
}
