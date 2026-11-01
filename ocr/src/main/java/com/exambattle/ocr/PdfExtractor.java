package com.exambattle.ocr;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;

/**
 * 逐頁處理 PDF：先讀文字層；文字太少（掃描頁）或指定強制 OCR 時，把該頁轉成 300 DPI 影像再辨識。
 */
final class PdfExtractor {

    /** 非空白字元少於此值視為掃描頁。 */
    static final int MIN_TEXT_LAYER_CHARS = 20;
    static final float OCR_DPI = 200f;

    interface PageListener {
        void pageDone(PageText page, int totalPages);
    }

    private final ImageOcr ocr;

    PdfExtractor(ImageOcr ocr) {
        this.ocr = ocr;
    }

    int pageCount(Path file) throws ExtractionException {
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            return doc.getNumberOfPages();
        } catch (IOException e) {
            throw unreadable(e);
        }
    }

    /** 逐頁擷取；cancelled 回傳 true 時停止並回傳 false。 */
    boolean extract(Path file, boolean forceOcr, PageListener listener, BooleanSupplier cancelled)
            throws ExtractionException {
        try (PDDocument doc = Loader.loadPDF(file.toFile())) {
            if (doc.isEncrypted()) {
                throw new ExtractionException("這份 PDF 有密碼保護，請先解除保護再匯入。");
            }
            int total = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < total; i++) {
                if (cancelled.getAsBoolean()) {
                    return false;
                }
                PageText page = null;
                if (!forceOcr) {
                    stripper.setStartPage(i + 1);
                    stripper.setEndPage(i + 1);
                    String text = stripper.getText(doc).strip();
                    PageText candidate = new PageText(i + 1, ExtractionMethod.TEXT_LAYER, text);
                    if (candidate.characterCount() >= MIN_TEXT_LAYER_CHARS) {
                        page = candidate;
                    }
                }
                if (page == null) {
                    BufferedImage image = renderer.renderImageWithDPI(i, OCR_DPI, ImageType.GRAY);
                    page = new PageText(i + 1, ExtractionMethod.OCR, ocr.recognize(image));
                }
                listener.pageDone(page, total);
            }
            return true;
        } catch (IOException e) {
            throw unreadable(e);
        }
    }

    private static ExtractionException unreadable(IOException e) {
        return new ExtractionException("無法讀取 PDF：" + e.getMessage(), e);
    }
}
