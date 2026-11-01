package com.exambattle.ocr;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageImagesTest {

    @TempDir
    Path dir;

    @Test
    void rendersPdfPagesAndImages() throws Exception {
        Path pdf = dir.resolve("two.pdf");
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.A4));
            doc.addPage(new PDPage(PDRectangle.A4));
            doc.save(pdf.toFile());
        }
        BufferedImage page2 = PageImages.render(pdf, 2, 72f);
        assertEquals(595, page2.getWidth(), 1, "A4 at 72 DPI");

        Path png = dir.resolve("q.png");
        ImageIO.write(new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB), "png", png.toFile());
        assertEquals(40, PageImages.render(png, 1, 72f).getWidth());

        assertThrows(ExtractionException.class, () -> PageImages.render(pdf, 3, 72f));
        assertThrows(ExtractionException.class, () -> PageImages.render(png, 2, 72f));
        ExtractionException missing = assertThrows(ExtractionException.class,
                () -> PageImages.render(dir.resolve("gone.pdf"), 1, 72f));
        assertTrue(missing.getMessage().contains("原始檔"));
    }
}
