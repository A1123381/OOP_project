package com.exambattle.ocr;

/** 一頁文字的來源。 */
public enum ExtractionMethod {
    /** PDF 內建的文字層。 */
    TEXT_LAYER,
    /** 以 Tesseract 辨識影像。 */
    OCR
}
