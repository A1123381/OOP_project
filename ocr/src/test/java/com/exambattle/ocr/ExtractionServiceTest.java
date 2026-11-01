package com.exambattle.ocr;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 擷取流程：PDF 文字層、掃描頁 OCR、圖片 OCR、限制與取消。
 * 需要 OCR 的測試只在專案根目錄的 tessdata 存在時執行（見 docs/OCR.md）。
 */
class ExtractionServiceTest {

    /** Maven 執行測試時的工作目錄是 ocr/，語言資料在專案根目錄。 */
    static final OcrConfig CONFIG = OcrConfig.locate(null, Map.of(), Path.of("..", "tessdata"));

    @TempDir
    Path dir;

    private final ExtractionService service = new ExtractionService(CONFIG);

    static boolean tessdataAvailable() {
        return Files.isRegularFile(CONFIG.tessdata().resolve("chi_tra.traineddata"))
                && Files.isRegularFile(CONFIG.tessdata().resolve("eng.traineddata"));
    }

    @Test
    void textLayerPdfDoesNotNeedOcr() throws Exception {
        Path pdf = dir.resolve("text.pdf");
        try (PDDocument doc = new PDDocument()) {
            addTextPage(doc, "1. Which number is prime? (A) 4 (B) 7 (C) 9 (D) 15");
            addTextPage(doc, "2. What is the capital of France? (A) Paris (B) Rome");
            doc.save(pdf.toFile());
        }
        // 沒有語言資料也能擷取文字層
        ExtractionService noOcr = new ExtractionService(OcrConfig.locate(null, Map.of(), dir.resolve("missing")));
        List<Integer> progress = new ArrayList<>();

        ExtractionResult result = noOcr.extract(pdf, false, (page, done, total) -> progress.add(done), () -> false);

        assertEquals("text.pdf", result.fileName(), "只記錄檔名，不記錄路徑");
        assertEquals(2, result.totalPages());
        assertEquals(List.of(1, 2), progress);
        assertTrue(result.pages().stream().allMatch(p -> p.method() == ExtractionMethod.TEXT_LAYER));
        assertTrue(result.pages().get(0).text().contains("Which number is prime?"));
        assertFalse(result.cancelled());

        List<DraftQuestion> drafts = QuestionSplitter.split(result.pages()).questions();
        assertEquals(List.of("Paris", "Rome"), drafts.get(1).options());
    }

    @Test
    @EnabledIf("tessdataAvailable")
    void scannedPdfPageIsRecognizedWithOcr() throws Exception {
        Path pdf = dir.resolve("scan.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            BufferedImage image = examImage("1. 下列何者為質數？", "(A) 4   (B) 7   (C) 9   (D) 15");
            PDImageXObject xobject = LosslessFactory.createFromImage(doc, image);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float width = PDRectangle.A4.getWidth() - 60;
                cs.drawImage(xobject, 30, 600, width, width * image.getHeight() / image.getWidth());
            }
            doc.save(pdf.toFile());
        }

        ExtractionResult result = service.extract(pdf, false, (p, d, t) -> { }, () -> false);

        PageText page = result.pages().get(0);
        assertEquals(ExtractionMethod.OCR, page.method(), "沒有文字層的頁面改用 OCR");
        assertTrue(page.text().contains("質數"), page.text());
        assertFalse(page.text().contains("質 數"), "中文字之間的空白已移除：" + page.text());
    }

    @Test
    @EnabledIf("tessdataAvailable")
    void pngIsRecognized() throws Exception {
        Path png = dir.resolve("question.png");
        ImageIO.write(examImage("2. 台灣最高的山是哪一座？", "(A) 玉山   (B) 雪山   (C) 合歡山"), "png", png.toFile());

        ExtractionResult result = service.extract(png, false, (p, d, t) -> { }, () -> false);

        String text = result.pages().get(0).text();
        assertTrue(text.contains("玉山") && text.contains("最高"), text);
        assertEquals(1, result.totalPages());
    }

    @Test
    void missingLanguageDataGivesHelpfulError() throws Exception {
        Path png = dir.resolve("q.png");
        ImageIO.write(examImage("1. test", "(A) a"), "png", png.toFile());
        ExtractionService noData = new ExtractionService(OcrConfig.locate(null, Map.of(), dir.resolve("missing")));

        ExtractionException e = assertThrows(ExtractionException.class,
                () -> noData.extract(png, false, (p, d, t) -> { }, () -> false));
        assertTrue(e.getMessage().contains("docs/OCR.md"), e.getMessage());
    }

    @Test
    void limitsAndUnsupportedTypes() throws Exception {
        Path doc = dir.resolve("notes.docx");
        Files.writeString(doc, "x");
        assertThrows(ExtractionException.class, () -> service.extract(doc, false, (p, d, t) -> { }, () -> false));

        Path big = dir.resolve("big.pdf");
        try (RandomAccessFile file = new RandomAccessFile(big.toFile(), "rw")) {
            file.setLength(ExtractionService.MAX_FILE_BYTES + 1);
        }
        ExtractionException tooBig = assertThrows(ExtractionException.class,
                () -> service.extract(big, false, (p, d, t) -> { }, () -> false));
        assertTrue(tooBig.getMessage().contains("MB"));

        Path many = dir.resolve("many.pdf");
        try (PDDocument pdf = new PDDocument()) {
            for (int i = 0; i <= ExtractionService.MAX_PDF_PAGES; i++) {
                pdf.addPage(new PDPage());
            }
            pdf.save(many.toFile());
        }
        ExtractionException tooMany = assertThrows(ExtractionException.class,
                () -> service.extract(many, false, (p, d, t) -> { }, () -> false));
        assertTrue(tooMany.getMessage().contains("頁"));

        Path broken = dir.resolve("broken.pdf");
        Files.writeString(broken, "not a pdf");
        assertThrows(ExtractionException.class, () -> service.extract(broken, false, (p, d, t) -> { }, () -> false));
    }

    @Test
    void cancellationKeepsFinishedPages() throws Exception {
        Path pdf = dir.resolve("three.pdf");
        try (PDDocument doc = new PDDocument()) {
            for (int i = 1; i <= 3; i++) {
                addTextPage(doc, i + ". This page has enough text to skip OCR completely.");
            }
            doc.save(pdf.toFile());
        }
        List<Integer> seen = new ArrayList<>();

        ExtractionResult result = service.extract(pdf, false, (p, d, t) -> seen.add(d), () -> seen.size() >= 1);

        assertTrue(result.cancelled());
        assertEquals(1, result.pages().size());
        assertEquals(3, result.totalPages());
    }

    @Test
    void configLocationPriority() {
        Path fallback = Path.of("fallback");
        assertEquals(Path.of("prop").toAbsolutePath(),
                OcrConfig.locate("prop", Map.of(OcrConfig.ENV, "env"), fallback).tessdata());
        assertEquals(Path.of("env").toAbsolutePath(),
                OcrConfig.locate(null, Map.of(OcrConfig.ENV, "env"), fallback).tessdata());
        assertEquals(fallback.toAbsolutePath(), OcrConfig.locate(" ", Map.of(), fallback).tessdata());
    }

    @Test
    void cleanUpRemovesSpacesBetweenChineseCharacters() {
        assertEquals("下列何者為質數？\n(A) 4 (B) 7\n\nEnglish words stay",
                ImageOcr.cleanUp("下 列 何者 為 質數 ？  \r\n(A) 4 (B) 7\n\n\n\nEnglish words stay\n"));
    }

    private static void addTextPage(PDDocument doc, String text) throws Exception {
        PDPage page = new PDPage(PDRectangle.A4);
        doc.addPage(page);
        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            cs.beginText();
            cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            cs.newLineAtOffset(40, 780);
            cs.showText(text);
            cs.endText();
        }
    }

    /** 以微軟正黑體繪製一題考題（模擬掃描）。 */
    static BufferedImage examImage(String... lines) {
        BufferedImage image = new BufferedImage(1600, 120 + 90 * lines.length, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setColor(Color.BLACK);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font("Microsoft JhengHei", Font.PLAIN, 52));
        for (int i = 0; i < lines.length; i++) {
            g.drawString(lines[i], 50, 110 + 90 * i);
        }
        g.dispose();
        return image;
    }
}
