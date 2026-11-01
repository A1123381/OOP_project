package com.exambattle.ocr;

import java.util.List;

/**
 * 擷取結果。fileName 只有檔名（不含路徑）；cancelled 表示使用者中途取消，pages 只含已完成的頁面。
 */
public record ExtractionResult(String fileName, int totalPages, List<PageText> pages, boolean cancelled) {
}
