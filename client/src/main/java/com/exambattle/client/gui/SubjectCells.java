package com.exambattle.client.gui;

import com.exambattle.common.protocol.bank.SubjectInfo;
import javafx.util.StringConverter;

/** 大分類在下拉選單與清單中的顯示方式。 */
final class SubjectCells {

    /** 「全部」選項；id 不會與資料庫中的分類衝突。 */
    static final SubjectInfo ALL = new SubjectInfo(-1, "（全部）");

    static final StringConverter<SubjectInfo> CONVERTER = new StringConverter<>() {
        @Override
        public String toString(SubjectInfo subject) {
            return subject == null ? "" : subject.name();
        }

        @Override
        public SubjectInfo fromString(String text) {
            throw new UnsupportedOperationException();
        }
    };

    private SubjectCells() {
    }
}
