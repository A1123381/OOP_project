package com.exambattle.ocr;

import com.exambattle.common.protocol.bank.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 題組切題（D-035）。測試文字是自製的，結構仿照大考英文試卷（克漏字、文意選填、篇章結構、閱讀測驗、混合題），
 * 不使用實際試題。
 */
class QuestionGroupSplitterTest {

    private static SplitResult split(String... pages) {
        List<PageText> list = new ArrayList<>();
        for (int i = 0; i < pages.length; i++) {
            list.add(new PageText(i + 1, ExtractionMethod.TEXT_LAYER, pages[i]));
        }
        return QuestionSplitter.split(list);
    }

    private static DraftQuestion question(SplitResult r, int number) {
        return r.questions().stream().filter(q -> q.number() == number).findFirst().orElseThrow();
    }

    private static final String HEADER_A = "第 1 頁 請用 2B 鉛筆作答 模擬考";
    private static final String HEADER_B = "模擬考 請用 2B 鉛筆作答 第 2 頁";

    /** 四頁的模擬試卷：每頁有重複的頁首與頁碼。 */
    private static SplitResult mockExam() {
        return split("""
                第 1 頁 請用 2B 鉛筆作答 模擬考
                共 4 頁 英文
                第 壹 部 分 、 選 擇 題 （ 占 50 分 ）
                一 、 詞 彙 題 （ 占 2 分 ）
                說明︰第1題至第2題為單選題，每題1分。
                1. The weather was so ______ that we stayed indoors all day.
                (A) bright (B) awful (C) gentle (D) mild
                2. She ______ the letter twice before sending it.
                (A) read (B) ran (C) rose (D) rode
                二 、 綜 合 測 驗 （ 占 3 分 ）
                說明︰第3題至第5題為單選題，每題1分。
                - 1 -
                """, """
                模擬考 請用 2B 鉛筆作答 第 2 頁
                英文 共 4 頁
                第 3 至 5 題為題組
                Tom opened a small bakery near the station. Every morning,   3   , he baked fresh bread
                before sunrise. His customers loved the smell, and soon the shop became   4   in the town.
                One day a famous chef visited the bakery and   5   .
                3. (A) as usual (B) by chance (C) at last (D) in vain
                4. (A) popular (B) empty (C) silent (D) narrow
                5. (A) left at once (B) asked for the recipe
                (C) closed the door (D) lost his way
                三 、 文 意 選 填 （ 占 3 分 ）
                說明：第6題至第8題為單選題，每題1分。
                第 6 至 8 題為題組
                Many people enjoy   6   in the park on weekends. Walking is a simple way to stay
                healthy, and it does not   7   any special equipment. Doctors say that even a short walk
                can   8   your mood.
                (A) require (B) improve (C) walking (D) forget (E) hide
                - 2 -
                """, """
                第 3 頁 請用 2B 鉛筆作答 模擬考
                共 4 頁 英文
                四 、 閱 讀 測 驗 （ 占 4 分 ）
                說明︰第9題至第10題為單選題，每題2分。
                第 9 至 10 題為題組
                In 1998, a small village planted 300 trees along its river. Twenty years later, the
                river banks were covered with forest, and birds that had disappeared returned. The
                villagers now hold a festival every spring to celebrate the trees.
                Visitors from 12 countries came to the festival last year.
                9. What is the passage mainly about?
                (A) A river festival. (B) A village that planted trees.
                (C) Birds in danger. (D) A trip abroad.
                10. How many trees were planted?
                (A) 12 (B) 20 (C) 300 (D) 1998
                - 3 -
                """, """
                模擬考 請用 2B 鉛筆作答 第 4 頁
                英文 共 4 頁
                第 貳 部 分 、 混 合 題 （ 占 4 分 ）
                說明︰本部分共有1題組，每一子題配分標於題末。限在答題卷標示題號的作答區內作答，
                並以規定用筆作答。
                第 11 至 12 題為題組
                A. Green Shop sells reusable bottles.
                B. Book Corner sells old books.
                11. Which shop would you visit for a gift? Explain in one sentence.
                （簡答題，2 分）
                12. Write the letter of the shop that sells books.
                第 參 部 分 、 非 選 擇 題 （ 占 10 分 ）
                一 、 中 譯 英 （ 占 4 分 ）
                1. 我每天早上走路上學。
                - 4 -
                """);
    }

    @Test
    void wholeExamIsSplitWithoutMarginsOrTitles() {
        SplitResult r = mockExam();

        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12),
                r.questions().stream().map(DraftQuestion::number).toList());
        for (DraftQuestion q : r.questions()) {
            String all = q.content() + String.join(" ", q.options());
            assertFalse(all.contains("頁"), "頁首頁尾沒有殘留：" + q);
            assertFalse(all.contains("綜合測驗") || all.contains("綜 合"), "大題標題沒有殘留：" + q);
            assertFalse(all.contains("說明"), "說明沒有殘留：" + q);
        }
        assertEquals(List.of("read", "ran", "rose", "rode"), question(r, 2).options(), "下一大題沒有併入第 2 題");
        assertEquals(4, r.groups().size());
    }

    @Test
    void clozeGroupMarksBlanksAndFillsStems() {
        SplitResult r = mockExam();
        DraftGroup g = r.groups().get(question(r, 3).groupIndex());

        assertEquals(3, g.firstNumber());
        assertEquals(5, g.lastNumber());
        assertEquals(2, g.sourcePage());
        assertTrue(g.passage().startsWith("Tom opened a small bakery near the station. Every morning, 【3】, he baked"),
                g.passage());
        assertTrue(g.passage().contains("became 【4】 in the town."), g.passage());
        assertTrue(g.passage().endsWith("visited the bakery and 【5】."), g.passage());
        assertTrue(g.warnings().isEmpty(), g.warnings().toString());

        DraftQuestion q3 = question(r, 3);
        assertEquals("第 3 格", q3.content());
        assertEquals(List.of("as usual", "by chance", "at last", "in vain"), q3.options());
        assertEquals(QuestionType.SINGLE_CHOICE, q3.guessedType());
        assertTrue(q3.warnings().isEmpty(), q3.warnings().toString());
        assertEquals(List.of("left at once", "asked for the recipe", "closed the door", "lost his way"),
                question(r, 5).options(), "選項分兩行");
        assertNull(question(r, 2).groupIndex());
    }

    @Test
    void sharedOptionPoolGeneratesOneQuestionPerBlank() {
        SplitResult r = mockExam();
        DraftQuestion q6 = question(r, 6);
        DraftGroup g = r.groups().get(q6.groupIndex());

        assertEquals("Many people enjoy 【6】 in the park on weekends. Walking is a simple way to stay healthy, "
                + "and it does not 【7】 any special equipment. Doctors say that even a short walk can 【8】 your mood.",
                g.passage());
        for (int n = 6; n <= 8; n++) {
            DraftQuestion q = question(r, n);
            assertEquals("第 " + n + " 格", q.content());
            assertEquals(List.of("require", "improve", "walking", "forget", "hide"), q.options());
            assertEquals(QuestionType.SINGLE_CHOICE, q.guessedType());
            assertEquals(q6.groupIndex(), q.groupIndex());
            assertEquals(2, q.sourcePage());
        }
    }

    @Test
    void readingGroupKeepsNumbersInPassage() {
        SplitResult r = mockExam();
        DraftGroup g = r.groups().get(question(r, 9).groupIndex());

        assertTrue(g.passage().startsWith("In 1998, a small village planted 300 trees"), g.passage());
        assertTrue(g.passage().contains("celebrate the trees.\nVisitors from 12 countries"), "短行結尾換段：" + g.passage());
        assertFalse(g.passage().contains("【"), "閱讀測驗不標記空格");
        assertEquals("What is the passage mainly about?", question(r, 9).content());
        assertEquals(List.of("12", "20", "300", "1998"), question(r, 10).options());
    }

    @Test
    void mixedSectionIsUnsupported() {
        SplitResult r = mockExam();

        for (int n = 11; n <= 12; n++) {
            DraftQuestion q = question(r, n);
            assertTrue(q.unsupported(), "第 " + n + " 題");
            assertNull(q.guessedType());
            assertTrue(q.warnings().stream().anyMatch(w -> w.contains("非選擇題")));
        }
        assertTrue(r.groups().get(question(r, 11).groupIndex()).passage().contains("Green Shop"));
        assertFalse(question(r, 12).content().contains("我每天"), "非選擇題部分的內容不會併入");
        assertFalse(question(r, 10).unsupported());
    }

    @Test
    void missingQuestionsInGroupRangeArePlaceholders() {
        SplitResult r = split("""
                第 1 至 3 題為題組
                這是一篇關於校園生活的文章，內容說明學生如何安排時間。
                1. 文章的主旨是什麼？
                (A) 運動 (B) 時間安排 (C) 飲食
                3. 作者建議怎麼做？
                (A) 早睡 (B) 晚睡 (C) 不睡
                """);

        assertEquals(List.of(1, 2, 3), r.questions().stream().map(DraftQuestion::number).toList());
        DraftQuestion q2 = r.questions().get(1);
        assertEquals("", q2.content());
        assertTrue(q2.warnings().stream().anyMatch(w -> w.contains("找不到這一題")));
        assertEquals(0, q2.groupIndex());
        assertEquals("這是一篇關於校園生活的文章，內容說明學生如何安排時間。", r.groups().get(0).passage());
    }

    @Test
    void otherGroupHeaderStyles() {
        SplitResult a = split("""
                請閱讀下文，回答第 1 至 2 題
                短文內容甲。
                1. 問題一的內容 (A) 甲 (B) 乙
                2. 問題二的內容 (A) 甲 (B) 乙
                3. 不在題組內的題目 (A) 甲 (B) 乙
                """);
        assertEquals(0, a.questions().get(1).groupIndex());
        assertNull(a.questions().get(2).groupIndex(), "超出範圍的題目不屬於題組");

        SplitResult b = split("""
                Questions 1-2
                A short passage about cats.
                1. What is it about? (A) Cats (B) Dogs
                2. Who wrote it? (A) Tom (B) Amy
                """);
        assertEquals("A short passage about cats.", b.groups().get(0).passage());
        assertEquals(0, b.questions().get(1).groupIndex());
    }

    @Test
    void clozeBlankNotFoundWarns() {
        SplitResult r = split("""
                第 1 至 2 題為題組
                The cat sat on the   1   and looked at the bird.
                1. (A) mat (B) hat
                2. (A) yes (B) no
                """);

        assertTrue(r.groups().get(0).passage().contains("【1】"));
        assertTrue(r.groups().get(0).warnings().stream().anyMatch(w -> w.contains("找不到第 2 格")),
                r.groups().get(0).warnings().toString());
    }

    @Test
    void longUnlabelledTextBecomesGuessedGroup() {
        String passage = "這是一段很長的文章，用來說明沒有題組標題時的處理方式。".repeat(10);
        SplitResult r = split("""
                1. 第一題的內容
                (A) 甲 (B) 乙
                %s
                2. 根據上文，下列何者正確？
                (A) 甲 (B) 乙
                """.formatted(passage));

        assertEquals(List.of("甲", "乙"), r.questions().get(0).options(), "文章不併入第 1 題的選項");
        assertNull(r.questions().get(0).groupIndex());
        DraftGroup g = r.groups().get(r.questions().get(1).groupIndex());
        assertEquals(passage, g.passage());
        assertNull(g.firstNumber());
        assertTrue(g.warnings().stream().anyMatch(w -> w.contains("可能是題組文章")));
    }

    @Test
    void imageLikeOptionsWarn() {
        SplitResult r = split("""
                1. Which map shows the route?
                (A) (B)
                North
                South
                Island Island
                OCEAN
                SEA SEA
                LAND
                (C) (D)
                North
                South
                OCEAN
                LAND
                """);

        assertTrue(r.questions().get(0).warnings().stream().anyMatch(w -> w.contains("圖片或圖表")),
                r.questions().get(0).warnings().toString());
    }

    @Test
    void chinesePassageJoinsLinesWithoutSpaces() {
        assertEquals("第一行是比較長的一段文字接著第二行。\n新段落", QuestionSplitter.joinPassage(List.of(
                "第一行是比較長的一段文字接著", "第二行。", "新段落")));
    }
}
