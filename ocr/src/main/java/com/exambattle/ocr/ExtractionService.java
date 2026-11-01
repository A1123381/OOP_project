package com.exambattle.ocr;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * 依檔案類型擷取文字：PDF 逐頁（文字層或 OCR），圖片直接 OCR。
 * 只讀取使用者選擇的本機檔案，不複製、不上傳（D-027）。結果只記錄檔名，不記錄路徑。
 */
public final class ExtractionService {

    public static final long MAX_FILE_BYTES = 50L * 1024 * 1024;
    public static final int MAX_PDF_PAGES = 100;
    public static final List<String> SUPPORTED_EXTENSIONS = List.of("pdf", "jpg", "jpeg", "png");

    /** 進度回報；在執行擷取的執行緒上呼叫。 */
    public interface ProgressListener {
        void progress(PageText page, int done, int total);
    }

    private final ImageOcr ocr;
    private final PdfExtractor pdf;

    public ExtractionService(OcrConfig config) {
        this(config, new ImageOcr(config));
    }

    ExtractionService(OcrConfig config, ImageOcr ocr) {
        this.ocr = ocr;
        this.pdf = new PdfExtractor(ocr);
    }

    /**
     * 擷取文字（阻塞，請在背景執行緒呼叫）。
     *
     * @param forceOcr  PDF 即使有文字層也改用 OCR
     * @param cancelled 回傳 true 時在下一頁開始前停止；已完成的頁面仍會回傳
     */
    public ExtractionResult extract(Path file, boolean forceOcr, ProgressListener listener, BooleanSupplier cancelled)
            throws ExtractionException {
        String extension = extensionOf(file);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new ExtractionException("不支援的檔案類型：" + file.getFileName() + "（支援 PDF、JPG、JPEG、PNG）");
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            throw new ExtractionException("無法讀取檔案：" + file.getFileName(), e);
        }
        if (size > MAX_FILE_BYTES) {
            throw new ExtractionException("檔案超過 " + MAX_FILE_BYTES / 1024 / 1024 + "MB，請分割後再匯入。");
        }
        String fileName = file.getFileName().toString();
        List<PageText> pages = new ArrayList<>();

        if (extension.equals("pdf")) {
            int total = pdf.pageCount(file);
            if (total > MAX_PDF_PAGES) {
                throw new ExtractionException("PDF 共 " + total + " 頁，超過上限 " + MAX_PDF_PAGES + " 頁，請分割後再匯入。");
            }
            boolean finished = pdf.extract(file, forceOcr, (page, n) -> {
                pages.add(page);
                listener.progress(page, pages.size(), n);
            }, cancelled);
            return new ExtractionResult(fileName, total, List.copyOf(pages), !finished);
        }

        if (cancelled.getAsBoolean()) {
            return new ExtractionResult(fileName, 1, List.of(), true);
        }
        BufferedImage image;
        try {
            image = ImageIO.read(file.toFile());
        } catch (IOException e) {
            throw new ExtractionException("無法讀取圖片：" + e.getMessage(), e);
        }
        if (image == null) {
            throw new ExtractionException("無法辨識圖片格式：" + fileName);
        }
        PageText page = new PageText(1, ExtractionMethod.OCR, ocr.recognize(image));
        listener.progress(page, 1, 1);
        return new ExtractionResult(fileName, 1, List.of(page), false);
    }

    static String extensionOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
