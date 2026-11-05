package com.exambattle.ocr;

/**
 * 頁面上的一個字詞與它的位置框，座標以頁面寬高的比例表示（0–1，左上角為原點），
 * 文字層 PDF 與 OCR 結果用同一種表示，方便換算成任何解析度的頁面圖片。
 */
public record TextBox(String text, double x, double y, double width, double height) {

    public double right() {
        return x + width;
    }

    public double bottom() {
        return y + height;
    }
}
