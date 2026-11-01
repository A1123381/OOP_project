package com.exambattle.server.dao;

/** 大分類名稱已存在。 */
public class SubjectExistsException extends Exception {

    public SubjectExistsException(String name) {
        super("分類已存在: " + name);
    }
}
