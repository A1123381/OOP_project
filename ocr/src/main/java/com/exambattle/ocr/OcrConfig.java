package com.exambattle.ocr;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Tesseract 語言資料的位置。優先順序：系統屬性 {@code exambattle.tessdata} →
 * 環境變數 {@code EXAMBATTLE_TESSDATA} → 工作目錄下的 {@code tessdata}。設定方式見 docs/OCR.md。
 */
public record OcrConfig(Path tessdata, String languages) {

    /** 繁體中文＋英文（D-029）。 */
    public static final String DEFAULT_LANGUAGES = "chi_tra+eng";
    public static final String PROPERTY = "exambattle.tessdata";
    public static final String ENV = "EXAMBATTLE_TESSDATA";

    public static OcrConfig locate() {
        return locate(System.getProperty(PROPERTY), System.getenv(), Path.of("tessdata"));
    }

    static OcrConfig locate(String property, Map<String, String> env, Path fallback) {
        String configured = property != null && !property.isBlank() ? property : env.get(ENV);
        Path dir = configured != null && !configured.isBlank() ? Path.of(configured.strip()) : fallback;
        return new OcrConfig(dir.toAbsolutePath(), DEFAULT_LANGUAGES);
    }

    /** 檢查語言資料是否齊全；缺少時丟出可直接顯示的錯誤。 */
    public void verify() throws ExtractionException {
        for (String lang : List.of(languages.split("\\+"))) {
            if (!Files.isRegularFile(tessdata.resolve(lang + ".traineddata"))) {
                throw new ExtractionException("找不到 OCR 語言資料 " + lang + ".traineddata（位置：" + tessdata
                        + "）。請依 docs/OCR.md 下載語言資料，或以環境變數 " + ENV + " 指定位置。");
            }
        }
    }
}
