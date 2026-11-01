package com.exambattle.ocr;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnswerKeyParserTest {

    private static Map<Integer, String> tokens(AnswerKeyParser.Result r) {
        java.util.Map<Integer, String> m = new java.util.TreeMap<>();
        r.answers().forEach((k, v) -> m.put(k, v.token()));
        return m;
    }

    @Test
    void inlinePairs() {
        AnswerKeyParser.Result r = AnswerKeyParser.parse("1.B 2.AC 3.○ 4.× 5.D");

        assertEquals(Map.of(1, "B", 2, "AC", 3, "○", 4, "×", 5, "D"), tokens(r));
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
    }

    @Test
    void variousSeparatorsAndOnePerLine() {
        AnswerKeyParser.Result r = AnswerKeyParser.parse("""
                標準答案
                1 (B)
                2、A,C
                3: 對
                4) X
                ５．Ｅ
                6. B、D、E
                """);

        assertEquals(Map.of(1, "B", 2, "AC", 3, "對", 4, "X", 5, "E", 6, "BDE"), tokens(r));
        assertTrue(r.warnings().isEmpty(), r.warnings().toString());
    }

    @Test
    void twoRowTable() {
        AnswerKeyParser.Result r = AnswerKeyParser.parse("""
                題號 1 2 3 4 5
                答案 C A BD ○ ×
                """);

        assertEquals(Map.of(1, "C", 2, "A", 3, "BD", 4, "○", 5, "×"), tokens(r));
    }

    @Test
    void tableWithMismatchedCountsWarns() {
        AnswerKeyParser.Result r = AnswerKeyParser.parse("題號 1 2 3\n答案 A B");

        assertEquals(Map.of(1, "A", 2, "B"), tokens(r));
        assertTrue(r.warnings().get(0).contains("數量不同"));
    }

    @Test
    void unparsableTextAndDuplicatesAreReported() {
        AnswerKeyParser.Result r = AnswerKeyParser.parse("1.A 2.? 1.B\n這行沒有答案 xyz");

        assertEquals("B", r.answers().get(1).token());
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("出現兩次")));
        assertTrue(r.warnings().stream().anyMatch(w -> w.contains("無法解析")));
    }

    @Test
    void emptyOrNoAnswers() {
        assertTrue(AnswerKeyParser.parse("").answers().isEmpty());
        assertTrue(AnswerKeyParser.parse("只有說明文字").warnings().stream().anyMatch(w -> w.contains("沒有找到")));
    }

    @Test
    void tokenInterpretation() {
        assertEquals(Optional.of(Set.of('A', 'C')), new AnswerKey("AC").letters());
        assertEquals(Optional.empty(), new AnswerKey("○").letters());
        assertEquals(Optional.of(true), new AnswerKey("○").trueFalse());
        assertEquals(Optional.of(true), new AnswerKey("O").trueFalse());
        assertEquals(Optional.of(false), new AnswerKey("X").trueFalse());
        // F 有兩種解讀，由套用時的題型決定
        assertEquals(Optional.of(Set.of('F')), new AnswerKey("F").letters());
        assertEquals(Optional.of(false), new AnswerKey("F").trueFalse());
        assertEquals(Optional.empty(), new AnswerKey("AB").trueFalse());
    }
}
