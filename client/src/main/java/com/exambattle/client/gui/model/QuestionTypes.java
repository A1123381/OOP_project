package com.exambattle.client.gui.model;

import com.exambattle.common.protocol.bank.QuestionType;

/** 題型的顯示名稱。 */
public final class QuestionTypes {

    private QuestionTypes() {
    }

    public static String label(QuestionType type) {
        return switch (type) {
            case SINGLE_CHOICE -> "單選";
            case MULTIPLE_CHOICE -> "多選";
            case TRUE_FALSE -> "是非";
        };
    }
}
