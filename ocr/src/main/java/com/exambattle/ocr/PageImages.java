package com.exambattle.ocr;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * 校正時對照原始頁面用：從使用者選擇的本機檔案即時轉出頁面圖片，只在記憶體中使用，
 * 不寫檔、不上傳（見 DECISIONS.md D-032）。
 */
public final class PageImages {

    /** 顯示用的解析度。 */
    public static final float DISPLAY_DPI = 110f;
    /** 截取題目圖片（自動截圖、框選）用的解析度，與 OCR 相同（D-045）。 */
    public static final float CROP_DPI = 200f;

    private PageImages() {
    }

    /** 頁碼從 1 開始；圖片檔只有第 1 頁。 */
    public static BufferedImage render(Path file, int page, float dpi) throws ExtractionException {
        String ext = ExtractionService.extensionOf(file);
        try {
            if (ext.equals("pdf")) {
                try (PDDocument doc = Loader.loadPDF(file.toFile())) {
                    if (page < 1 || page > doc.getNumberOfPages()) {
                        throw new ExtractionException("頁碼超出範圍：" + page);
                    }
                    return new PDFRenderer(doc).renderImageWithDPI(page - 1, dpi, ImageType.RGB);
                }
            }
            if (page != 1) {
                throw new ExtractionException("圖片只有 1 頁");
            }
            BufferedImage image = ImageIO.read(file.toFile());
            if (image == null) {
                throw new ExtractionException("無法讀取圖片：" + file.getFileName());
            }
            return image;
        } catch (IOException e) {
            throw new ExtractionException("無法讀取原始檔：" + e.getMessage(), e);
        }
    }
}
