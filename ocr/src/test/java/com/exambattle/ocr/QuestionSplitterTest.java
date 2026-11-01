package com.exambattle.ocr;

import com.exambattle.common.protocol.bank.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionSplitterTest {

    private static List<DraftQuestion> split(String... pages) {
        List<PageText> list = new java.util.ArrayList<>();
        for (int i = 0; i < pages.length; i++) {
            list.add(new PageText(i + 1, ExtractionMethod.TEXT_LAYER, pages[i]));
        }
        return QuestionSplitter.split(list).questions();
    }

    @Test
    void optionsOnSeparateLines() {
        List<DraftQuestion> qs = split("""
                113 學年度數學科期中考
                1. 下列何者為質數？
                (A) 4
                (B) 7
                (C) 9
                (D) 15
                2. 函數 f(x) = 2x + 3，則 f(4) = ?
                (A) 8 (B) 11 (C) 14 (D) 7
                """);

        assertEquals(2, qs.size());
        DraftQuestion q1 = qs.get(0);
        assertEquals(1, q1.number());
        assertEquals("下列何者為質數？", q1.content());
        assertEquals(List.of("4", "7", "9", "15"), q1.options());
        assertEquals(QuestionType.SINGLE_CHOICE, q1.guessedType());
        assertTrue(q1.warnings().isEmpty(), q1.warnings().toString());
        assertEquals(List.of("8", "11", "14", "7"), qs.get(1).options(), "同一行的選項");
        assertEquals("函數 f(x) = 2x + 3，則 f(4) = ?", qs.get(1).content());
    }

    @Test
    void fullWidthAndOtherNumberingStyles() {
        List<DraftQuestion> qs = split("""
                （１）台灣最高的山是？（Ａ）玉山（Ｂ）雪山（Ｃ）合歡山
                (2) Which word is a noun? A. run B. apple C. quickly
                第3題 是否同意？
                4、 太陽從東方升起
                """);

        assertEquals(List.of(1, 2, 3, 4), qs.stream().map(DraftQuestion::number).toList());
        assertEquals(List.of("玉山", "雪山", "合歡山"), qs.get(0).options());
        assertEquals(List.of("run", "apple", "quickly"), qs.get(1).options());
        assertEquals("Which word is a noun?", qs.get(1).content());
        assertTrue(qs.get(2).options().isEmpty());
        assertNull(qs.get(2).guessedType(), "沒有選項時題型由使用者決定");
    }

    @Test
    void multiLineStemAndOptionsAcrossPages() {
        List<DraftQuestion> qs = split("""
                1. 下列敘述
                何者正確？
                (A) 甲
                (B) 乙""", """
                (C) 丙
                (D) 丁
                2. 下一題的內容
                (A) 一 (B) 二
                """);

        assertEquals("下列敘述\n何者正確？", qs.get(0).content());
        assertEquals(List.of("甲", "乙", "丙", "丁"), qs.get(0).options(), "選項跨頁");
        assertEquals(1, qs.get(0).sourcePage());
        assertEquals(2, qs.get(1).sourcePage());
    }

    @Test
    void sectionHeadersGuessTypes() {
        List<DraftQuestion> qs = split("""
                一、單選題
                1. 單選題目內容 (A) 甲 (B) 乙
                二、多選題（每題 5 分）
                2. 多選題目內容 (A) 甲 (B) 乙 (C) 丙
                三、是非題
                3. 地球是圓的。
                """);

        assertEquals(QuestionType.SINGLE_CHOICE, qs.get(0).guessedType());
        assertEquals(QuestionType.MULTIPLE_CHOICE, qs.get(1).guessedType());
        assertEquals(QuestionType.TRUE_FALSE, qs.get(2).guessedType());
        assertEquals("地球是圓的。", qs.get(2).content());
        assertEquals(List.of("甲", "乙", "丙"), qs.get(1).options(), "分段標題不會併入前一題");
    }

    @Test
    void warningsForSuspiciousResults() {
        List<DraftQuestion> qs = split("""
                1. 只有一個選項的題目 (A) 甲
                2. 選項不連續的題目 (A) 甲 (C) 丙 (D) 丁
                5. 跳號的題目內容
                (A) 一 (B) 二
                6. 短
                """);

        assertTrue(qs.get(0).warnings().stream().anyMatch(w -> w.contains("只找到 1 個選項")));
        assertTrue(qs.get(1).warnings().stream().anyMatch(w -> w.contains("不連續")));
        assertEquals(List.of("甲", "丙", "丁"), qs.get(1).options());
        assertTrue(qs.get(2).warnings().stream().anyMatch(w -> w.contains("題號不連續")));
        assertTrue(qs.get(3).warnings().stream().anyMatch(w -> w.contains("找不到選項")));
        assertTrue(qs.get(3).warnings().stream().anyMatch(w -> w.contains("過短")));
    }

    @Test
    void decimalsAndInnerNumbersAreNotQuestionStarts() {
        List<DraftQuestion> qs = split("""
                1. 下列數值何者最大？
                2.5 公尺與 3 公尺相比
                (1) 甲說 (2) 乙說
                (A) 2.5 (B) 3
                2. 第二題的內容很長
                (A) 是 (B) 否
                """);

        assertEquals(2, qs.size(), "2.5 與 (1)(2) 不是新題目");
        assertTrue(qs.get(0).content().contains("2.5 公尺"));
        assertEquals(List.of("2.5", "3"), qs.get(0).options());
    }

    @Test
    void textBeforeFirstQuestionAndAnswersAreNotGuessed() {
        List<DraftQuestion> qs = split("""
                國立某某大學 期末考
                注意事項：請用 2B 鉛筆作答
                1. 1 + 1 = ? (A) 1 (B) 2
                答案：B
                """);

        assertEquals(1, qs.size());
        assertEquals("1 + 1 = ?", qs.get(0).content());
        // 「答案：B」只是文字：不會被當成正確答案，也不會被當成選項記號，保留給使用者在第七階段處理
        assertEquals(List.of("1", "2 答案：B"), qs.get(0).options());
    }

    @Test
    void toleratesDamagedOcrOptionMarkersWithWarning() {
        // 取自實際 OCR 輸出：(B) 漏了括號、(C) 被讀成 (0、(E) 被讀成 (pp
        List<DraftQuestion> qs = split("""
                1. 函數f(x) = 2x+3,則f(4) 的值為何 ?
                (A) 8 B11 (014 (D)7
                2. 下列哪些數是質數 ?
                A2 (B9 (011 (D15 (pp17
                3. 下列何者正確 ?
                A)8 B)9 (O14 (D) 7
                """);

        assertEquals(List.of("8", "11", "14", "7"), qs.get(0).options());
        assertTrue(qs.get(0).warnings().stream().anyMatch(w -> w.contains("辨識不完整")));
        assertEquals(List.of("2", "9", "11", "15 (pp17"), qs.get(1).options(), "E 無法辨識時併入前一個選項，由使用者校正");
        assertEquals(List.of("8", "9", "14", "7"), qs.get(2).options());
    }

    @Test
    void damagedMarkersOutOfSequenceAreIgnored() {
        List<DraftQuestion> qs = split("""
                1. 維生素 B2 與 C3 的比較，下列何者正確？
                (A) 甲 (B) 乙
                """);

        assertEquals("維生素 B2 與 C3 的比較，下列何者正確？", qs.get(0).content());
        assertEquals(List.of("甲", "乙"), qs.get(0).options());
        assertTrue(qs.get(0).warnings().isEmpty(), qs.get(0).warnings().toString());
    }

    @Test
    void trueFalseSectionHasNoMissingOptionWarning() {
        List<DraftQuestion> qs = split("""
                是非題
                1. 任意三角形的內角和為 180 度。
                """);

        assertTrue(qs.get(0).warnings().isEmpty(), qs.get(0).warnings().toString());
    }

    @Test
    void noQuestionNumbersGivesEmptyResult() {
        assertTrue(split("這一頁只有說明文字，沒有題號。").isEmpty());
        assertTrue(split("").isEmpty());
    }

    @Test
    void normalizeConvertsFullWidth() {
        assertEquals("(A)12.", QuestionSplitter.normalize("（Ａ）１２．"));
    }
}
