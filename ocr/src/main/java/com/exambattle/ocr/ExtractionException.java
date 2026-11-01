package com.exambattle.ocr;

/** 擷取或辨識失敗，訊息可直接顯示給使用者。 */
public class ExtractionException extends Exception {

    public ExtractionException(String message) {
        super(message);
    }

    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
