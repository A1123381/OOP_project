package com.exambattle.ocr;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 一頁中每個字詞的位置（自動截圖用，D-045），只在本機讀取原始檔：
 * 文字層 PDF 由 PDFBox 讀出每個字的座標；掃描頁與圖片由 Tesseract 回傳逐字位置框。
 */
public final class PageWords {

    private PageWords() {
    }

    /** 頁碼從 1 開始；method 為擷取這一頁時使用的方式。 */
    public static List<TextBox> of(Path file, int page, ExtractionMethod method, OcrConfig config)
            throws ExtractionException {
        if (method == ExtractionMethod.TEXT_LAYER && ExtractionService.extensionOf(file).equals("pdf")) {
            return fromTextLayer(file, page);
        }
        float dpi = ExtractionService.extensionOf(file).equals("pdf") ? PdfExtractor.OCR_DPI : 0;
        BufferedImage image = PageImages.render(file, page, dpi);
        return new ImageOcr(config).words(image);
    }

    static List<TextBox> fromTextLayer(Path file, int page) throws ExtractionException {
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            if (page < 1 || page > doc.getNumberOfPages()) {
                throw new ExtractionException("頁碼超出範圍：" + page);
            }
            PDPage p = doc.getPage(page - 1);
            PDRectangle box = p.getCropBox();
            Collector collector = new Collector(box.getWidth(), box.getHeight());
            collector.setSortByPosition(true);
            collector.setStartPage(page);
            collector.setEndPage(page);
            collector.getText(doc);
            collector.flush();
            return List.copyOf(collector.words);
        } catch (IOException e) {
            throw new ExtractionException("無法讀取 PDF：" + e.getMessage(), e);
        }
    }

    /** 把 PDFBox 的字元位置合併成以空白分隔的字詞。 */
    private static final class Collector extends PDFTextStripper {
        final double pageWidth;
        final double pageHeight;
        final List<TextBox> words = new ArrayList<>();
        private final StringBuilder text = new StringBuilder();
        private double left = Double.MAX_VALUE;
        private double top = Double.MAX_VALUE;
        private double right = -1;
        private double bottom = -1;

        Collector(double pageWidth, double pageHeight) throws IOException {
            this.pageWidth = pageWidth;
            this.pageHeight = pageHeight;
        }

        @Override
        protected void writeString(String string, List<TextPosition> positions) {
            for (TextPosition t : positions) {
                String s = t.getUnicode();
                if (s == null || s.isBlank()) {
                    flush();
                    continue;
                }
                text.append(s);
                left = Math.min(left, t.getXDirAdj());
                right = Math.max(right, t.getXDirAdj() + t.getWidthDirAdj());
                top = Math.min(top, t.getYDirAdj() - t.getHeightDir());
                bottom = Math.max(bottom, t.getYDirAdj());
            }
            flush();
        }

        void flush() {
            if (!text.isEmpty()) {
                words.add(new TextBox(text.toString(), left / pageWidth, top / pageHeight,
                        (right - left) / pageWidth, (bottom - top) / pageHeight));
            }
            text.setLength(0);
            left = Double.MAX_VALUE;
            top = Double.MAX_VALUE;
            right = -1;
            bottom = -1;
        }
    }
}
