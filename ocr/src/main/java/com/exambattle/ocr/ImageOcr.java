package com.exambattle.ocr;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.regex.Pattern;

/**
 * 以 Tesseract（Tess4J）辨識影像。每次辨識都建立新的 Tesseract 實例，避免共用狀態。
 * 辨識結果會移除中文字之間多餘的空白（Tesseract 常在中文字之間插入空格）。
 */
public final class ImageOcr {

    /** 兩個中日韓字元（含全形標點）之間的空白。 */
    private static final Pattern CJK_GAP = Pattern.compile(
            "(?<=[\\p{IsHan}\\p{InCJKSymbolsAndPunctuation}\\p{InHalfwidthAndFullwidthForms}])[ \\t]+"
                    + "(?=[\\p{IsHan}\\p{InCJKSymbolsAndPunctuation}\\p{InHalfwidthAndFullwidthForms}])");

    /** 與 PdfExtractor 轉影像的解析度一致。 */
    static final int ASSUMED_DPI = 300;

    private final OcrConfig config;

    public ImageOcr(OcrConfig config) {
        this.config = config;
    }

    /** 辨識前先確認語言資料存在，只有文字層的 PDF 不需要語言資料。 */
    public String recognize(BufferedImage image) throws ExtractionException {
        config.verify();
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(config.tessdata().toString());
        tesseract.setLanguage(config.languages());
        // 沒有解析度資訊的圖片，Tesseract 會假設很低的 DPI 而嚴重影響中文辨識；統一以 300 DPI 處理
        tesseract.setVariable("user_defined_dpi", String.valueOf(ASSUMED_DPI));
        // 避免在中文字之間插入空白
        tesseract.setVariable("preserve_interword_spaces", "1");
        try {
            return cleanUp(tesseract.doOCR(grayscale(image)));
        } catch (TesseractException | Error e) {
            // 原生函式庫載入失敗時是 Error（UnsatisfiedLinkError 等）
            throw new ExtractionException("OCR 辨識失敗：" + e.getMessage()
                    + "。若是原生函式庫無法載入，請見 docs/OCR.md 的常見問題。", e);
        }
    }

    /** 逐字位置框（自動截圖用，D-045），座標換算成頁面比例。 */
    public java.util.List<TextBox> words(BufferedImage image) throws ExtractionException {
        config.verify();
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(config.tessdata().toString());
        tesseract.setLanguage(config.languages());
        tesseract.setVariable("user_defined_dpi", String.valueOf(ASSUMED_DPI));
        try {
            double w = image.getWidth();
            double h = image.getHeight();
            return tesseract.getWords(grayscale(image), net.sourceforge.tess4j.ITessAPI.TessPageIteratorLevel.RIL_WORD)
                    .stream()
                    .filter(word -> word.getText() != null && !word.getText().isBlank())
                    .map(word -> {
                        java.awt.Rectangle r = word.getBoundingBox();
                        return new TextBox(word.getText().strip(), r.x / w, r.y / h, r.width / w, r.height / h);
                    })
                    .toList();
        } catch (RuntimeException | Error e) {
            throw new ExtractionException("OCR 辨識失敗：" + e.getMessage(), e);
        }
    }

    /** 移除中文字之間的空白、行尾空白與多餘空行。 */
    static String cleanUp(String text) {
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        StringBuilder out = new StringBuilder();
        int blank = 0;
        for (String line : lines) {
            String cleaned = CJK_GAP.matcher(line).replaceAll("").stripTrailing();
            if (cleaned.isBlank()) {
                blank++;
                if (blank > 1) {
                    continue;
                }
            } else {
                blank = 0;
            }
            out.append(cleaned).append('\n');
        }
        return out.toString().strip();
    }

    private static BufferedImage grayscale(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_BYTE_GRAY) {
            return image;
        }
        BufferedImage gray = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D g = gray.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return gray;
    }
}
