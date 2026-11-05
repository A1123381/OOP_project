package com.exambattle.ocr;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 圖片選項的自動截圖範圍（D-045），用自製的字詞座標測試。 */
class OptionRegionsTest {

    private static TextBox word(String text, double x, double y) {
        return new TextBox(text, x, y, 0.03, 0.012);
    }

    /** 兩欄兩列的圖形選項：(A)(B) 一列，圖在下方；(C)(D) 一列；下一題在 0.9。 */
    private static List<TextBox> twoByTwo() {
        List<TextBox> words = new ArrayList<>();
        words.add(word("5.", 0.10, 0.10));
        words.add(word("Which", 0.14, 0.10));
        words.add(word("(A)", 0.12, 0.15));
        words.add(word("(B)", 0.52, 0.15));
        words.add(word("North", 0.20, 0.25)); // 圖上的零碎文字
        words.add(word("(C)", 0.12, 0.50));
        words.add(word("(D)", 0.52, 0.50));
        words.add(word("ANTARCTICA", 0.30, 0.80));
        words.add(word("6.", 0.10, 0.90));
        return words;
    }

    @Test
    void twoColumnsTwoRows() {
        List<Region> regions = OptionRegions.locate(twoByTwo(), 3, 5, 6, 4).orElseThrow();

        assertEquals(4, regions.size());
        Region a = regions.get(0);
        Region b = regions.get(1);
        Region c = regions.get(2);
        assertEquals(3, a.page());
        assertTrue(a.x() < 0.12 && a.x() + a.width() <= 0.52, "A 到 B 的記號為止：" + a);
        assertTrue(b.x() < 0.52 && b.x() + b.width() <= OptionRegions.PAGE_RIGHT + 1e-9, "B 到內容右邊界：" + b);
        assertTrue(a.y() < 0.15 && a.y() + a.height() < 0.50, "第一列到第二列的記號為止：" + a);
        assertTrue(c.y() + c.height() < 0.90, "第二列到下一題題號為止：" + c);
    }

    @Test
    void textOptionsOnOneLineAreNotCropped() {
        List<TextBox> words = List.of(word("1.", 0.1, 0.1), word("(A)", 0.12, 0.13), word("cat", 0.16, 0.13),
                word("(B)", 0.3, 0.13), word("dog", 0.34, 0.13), word("(C)", 0.5, 0.13), word("(D)", 0.7, 0.13),
                word("2.", 0.1, 0.145));

        assertEquals(Optional.empty(), OptionRegions.locate(words, 1, 1, 2, 4), "只夠放一行字：文字選項");
    }

    @Test
    void missingMarkerGivesNothing() {
        List<TextBox> words = new ArrayList<>(twoByTwo());
        words.removeIf(w -> w.text().equals("(C)"));

        assertEquals(Optional.empty(), OptionRegions.locate(words, 1, 5, 6, 4));
        assertEquals(Optional.empty(), OptionRegions.locate(twoByTwo(), 1, 99, null, 4), "找不到題號");
    }

    @Test
    void cropTrimsWhitespace() {
        BufferedImage page = new BufferedImage(1000, 1000, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = page.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 1000, 1000);
        g.setColor(Color.BLACK);
        g.fillRect(300, 300, 100, 50);
        g.dispose();

        BufferedImage cropped = new Region(1, 0.2, 0.2, 0.5, 0.5).cropTrimmed(page);

        assertEquals(100 + 12, cropped.getWidth(), "黑色方塊加上兩側邊距");
        assertEquals(50 + 12, cropped.getHeight());
    }
}
