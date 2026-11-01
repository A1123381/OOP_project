package com.exambattle.ocr;

/** 一頁擷取出的文字；page 從 1 開始。 */
public record PageText(int page, ExtractionMethod method, String text) {

    /** 非空白字元數。 */
    public int characterCount() {
        return (int) text.codePoints().filter(c -> !Character.isWhitespace(c)).count();
    }
}
