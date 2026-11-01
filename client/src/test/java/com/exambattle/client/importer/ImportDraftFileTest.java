package com.exambattle.client.importer;

import com.exambattle.client.importer.ImportDraftFile.ImportDraft;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.ocr.AnswerKey;
import com.exambattle.ocr.DraftQuestion;
import com.exambattle.ocr.ExtractionMethod;
import com.exambattle.ocr.PageText;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportDraftFileTest {

    @TempDir
    Path dir;

    private static final List<PageText> PAGES = List.of(new PageText(1, ExtractionMethod.OCR, "1. 題目\n(A) 甲 (B) 乙"));

    @Test
    void v2RoundTripKeepsCorrectionsAndSelection() throws Exception {
        ImportItem item = ImportItem.from(new DraftQuestion(1, "題目", List.of("甲", "乙"),
                QuestionType.SINGLE_CHOICE, 1, List.of("題目內容過短，可能辨識不完整")));
        AnswerKeyApplier.apply(List.of(item), Map.of(1, new AnswerKey("B")));
        ImportItem done = ImportItem.blank(2, 1);
        done.markImported();
        Path file = dir.resolve("exam." + ImportDraftFile.EXTENSION);

        ImportDraftFile.save(file, ImportDraftFile.create("C:\\Users\\someone\\秘密資料夾\\期中考.pdf", PAGES, List.of(item, done)));
        ImportDraft loaded = ImportDraftFile.load(file);
        List<ImportItem> items = ImportDraftFile.items(loaded);

        assertEquals(PAGES, loaded.pages());
        assertEquals(2, loaded.version());
        assertEquals(item.toData(), items.get(0).toData());
        assertEquals(ImportItem.Status.READY, items.get(0).status());
        assertEquals(ImportItem.Status.IMPORTED, items.get(1).status());
    }

    @Test
    void readsVersion1DraftsFromPhase6() throws Exception {
        Path file = dir.resolve("old.json");
        Files.writeString(file, """
                {"format":"exam-battle-import-draft","version":1,"fileName":"期中考.pdf","createdAt":"2026-11-01T00:00:00Z",
                 "pages":[{"page":1,"method":"OCR","text":"1. 題目"}],
                 "questions":[{"number":1,"content":"題目","options":["甲","乙"],"guessedType":"SINGLE_CHOICE",
                               "sourcePage":1,"warnings":[]}]}""");

        List<ImportItem> items = ImportDraftFile.items(ImportDraftFile.load(file));

        assertEquals(1, items.size());
        assertEquals(QuestionType.SINGLE_CHOICE, items.get(0).draft().type());
        assertEquals(ImportItem.Status.NO_ANSWER, items.get(0).status());
    }

    @Test
    void storesOnlyTheFileNameAndNoImages() throws Exception {
        Path file = dir.resolve("d." + ImportDraftFile.EXTENSION);
        String source = dir.resolve("private").resolve("期中考.pdf").toString();

        ImportDraftFile.save(file, ImportDraftFile.create(source, List.of(), List.of()));

        String json = Files.readString(file);
        assertEquals("期中考.pdf", ImportDraftFile.load(file).fileName());
        assertFalse(json.contains("private"), "草稿不可包含原始檔案的路徑：" + json);
        assertFalse(json.contains("questions"), "v2 只存 items");
        assertTrue(json.length() < 2_000, "不含圖片等大型資料");
    }

    @Test
    void rejectsOtherFiles() throws Exception {
        Path other = dir.resolve("other.json");
        Files.writeString(other, "{\"format\":\"something-else\",\"version\":1}");
        assertThrows(IOException.class, () -> ImportDraftFile.load(other));

        Path broken = dir.resolve("broken.json");
        Files.writeString(broken, "not json");
        assertThrows(IOException.class, () -> ImportDraftFile.load(broken));

        Path future = dir.resolve("future.json");
        Files.writeString(future, "{\"format\":\"" + ImportDraftFile.FORMAT + "\",\"version\":99}");
        assertThrows(IOException.class, () -> ImportDraftFile.load(future));
    }
}
