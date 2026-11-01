package com.exambattle.common.protocol.practice;

/**
 * 答案揭曉時機：AFTER_SUBMIT 為考試模式（交卷後一次批改，可前後修改），
 * IMMEDIATE 為練習模式（每題送出後立即顯示對錯，不能修改）。
 */
public enum RevealMode {
    AFTER_SUBMIT,
    IMMEDIATE
}
