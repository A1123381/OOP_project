package com.exambattle.server.dao;

/** 註冊的帳號已存在。 */
public class UsernameTakenException extends Exception {

    public UsernameTakenException(String username) {
        super("帳號已存在: " + username);
    }
}
