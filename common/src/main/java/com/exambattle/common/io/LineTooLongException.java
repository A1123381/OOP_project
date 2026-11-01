package com.exambattle.common.io;

import java.io.IOException;

/** 單行超過長度上限。 */
public class LineTooLongException extends IOException {

    public LineTooLongException(int maxChars) {
        super("單行超過 " + maxChars + " 個字元");
    }
}
