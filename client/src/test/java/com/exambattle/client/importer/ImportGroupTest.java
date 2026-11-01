package com.exambattle.client.importer;

import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.ocr.DraftGroup;
import com.exambattle.ocr.DraftQuestion;
import com.exambattle.ocr.ExtractionMethod;
import com.exambattle.ocr.PageText;
import com.exambattle.ocr.SplitResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 匯入校正中的題組（D-034）。 */
class ImportGroupTest {

    @TempDir
    Path dir;

    private static DraftQuestion q(int number, Integer group, boolean unsupported) {
        return new DraftQuestion(number, "第 " + number + " 格", unsupported ? List.of() : List.of("甲", "乙"),
                unsupported ? null : QuestionType.SINGLE_CHOICE, 1, List.of(), group, unsupported);
    }

    private static SplitResult split() {
        return new SplitResult(
                List.of(new DraftGroup("文章【1】【2】", 1, 2, 1, List.of()), new DraftGroup("非選擇題文章", 4, 4, 2, List.of())),
                List.of(q(1, 0, false), q(2, 0, false), q(3, null, false), q(4, 1, true)));
    }

    @Test
    void sameGroupSharesOnePassage() {
        List<ImportItem> items = ImportItem.fromSplit(split());

        assertSame(items.get(0).group(), items.get(1).group());
        assertNull(items.get(2).group());
        assertEquals("題組 1–2", items.get(0).group().label());
        items.get(0).group().setPassage("改過");
        assertEquals("改過", items.get(1).group().passage(), "同組一起改變");
        assertFalse(items.get(3).include(), "非選擇題預設不勾選");
        assertTrue(items.get(3).unsupported());
    }

    @Test
    void emptyPassageNeedsFix() {
        List<ImportItem> items = ImportItem.fromSplit(split());
        items.get(0).group().setPassage("  ");

        assertEquals(ImportItem.Status.NEEDS_FIX, items.get(0).status());
        assertEquals("題組文章不可空白", items.get(1).problem().orElseThrow());
        assertEquals(ImportItem.Status.NO_ANSWER, items.get(2).status());
    }

    @Test
    void requestPartsOnlyIncludeUsedGroups() {
        List<ImportItem> items = ImportItem.fromSplit(split());

        ImportItem.RequestParts parts = ImportItem.requestParts(List.of(items.get(2), items.get(1), items.get(0)));

        assertEquals(List.of("文章【1】【2】"), parts.groups());
        assertEquals(Arrays.asList(null, 0, 0), parts.groupIndexes());
        assertEquals(3, parts.questions().size());
    }

    @Test
    void draftKeepsGroupsAndReadsVersion2() throws Exception {
        List<ImportItem> items = ImportItem.fromSplit(split());
        Path file = dir.resolve("g." + ImportDraftFile.EXTENSION);
        ImportDraftFile.save(file, ImportDraftFile.create("英文.pdf",
                List.of(new PageText(1, ExtractionMethod.TEXT_LAYER, "x")), items));

        List<ImportItem> loaded = ImportDraftFile.items(ImportDraftFile.load(file));

        assertSame(loaded.get(0).group(), loaded.get(1).group(), "讀回後同組仍共用同一個題組");
        assertEquals("文章【1】【2】", loaded.get(0).group().passage());
        assertNull(loaded.get(2).group());
        assertTrue(loaded.get(3).unsupported());
        assertFalse(loaded.get(3).include());

        Path v2 = dir.resolve("v2.json");
        Files.writeString(v2, """
                {"format":"exam-battle-import-draft","version":2,"fileName":"a.pdf","createdAt":"2026-11-01T00:00:00Z",
                 "pages":[],"items":[{"include":true,"imported":false,"number":1,"sourcePage":1,"warnings":[],
                 "type":"SINGLE_CHOICE","content":"題目","explanation":"","options":[{"content":"甲","correct":true},
                 {"content":"乙","correct":false}],"trueFalseAnswer":null}]}""");
        List<ImportItem> old = ImportDraftFile.items(ImportDraftFile.load(v2));
        assertNull(old.get(0).group());
        assertEquals(ImportItem.Status.READY, old.get(0).status());
    }

    @Test
    void unsupportedQuestionIsNotGivenAnAnswer() {
        List<ImportItem> items = ImportItem.fromSplit(split());

        AnswerKeyApplier.Summary s = AnswerKeyApplier.apply(items, Map.of(4, new com.exambattle.ocr.AnswerKey("ADE")));

        assertEquals(0, s.applied());
        assertTrue(s.problems().get(0).contains("非選擇題"), s.problems().toString());
    }
}
