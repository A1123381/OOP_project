package com.exambattle.client.importer;

import com.exambattle.client.importer.ImportDraftFile.ImportDraft;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.ocr.AnswerKey;
import com.exambattle.ocr.DraftQuestion;
import com.exambattle.ocr.ExtractionMethod;
import com.exambattle.ocr.PageText;
import com.exambattle.client.gui.model.DraftImage;
import com.exambattle.client.gui.model.QuestionDraft;
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
    void roundTripKeepsCorrectionsAndSelection() throws Exception {
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
        assertEquals(ImportDraftFile.VERSION, loaded.version());
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
        assertFalse(json.contains("questions"), "v2 起只存 items");
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

    @Test
    void version4KeepsLocalImagesAndGroupImage() throws Exception {
        DraftImage map = DraftImage.local(new byte[] {1, 2, 3, 4}, "image/png");
        DraftImage chart = DraftImage.local(new byte[] {5, 6}, "image/jpeg");
        ImportGroup group = ImportGroup.manual("閱讀下文", 1, 2, 1);
        group.setImage(chart);
        ImportItem item = ImportItem.from(new DraftQuestion(1, "哪一張地圖？", List.of("", ""),
                QuestionType.SINGLE_CHOICE, 1, List.of()), group);
        var draft = item.draft();
        item.setDraft(draft.withOptions(List.of(draft.options().get(0).withImage(map), draft.options().get(1).withImage(map)))
                .withImage(chart));
        Path file = dir.resolve("img." + ImportDraftFile.EXTENSION);

        ImportDraftFile.save(file, ImportDraftFile.create("考卷.pdf", PAGES, List.of(item)));
        List<ImportItem> loaded = ImportDraftFile.items(ImportDraftFile.load(file));

        QuestionDraft back = loaded.get(0).draft();
        assertEquals(map, back.options().get(0).image());
        assertEquals(chart, back.image());
        assertEquals(chart, loaded.get(0).group().image());
        assertEquals(List.of(map, chart), ImportItem.localImages(loaded), "同內容的圖片只上傳一次");

        ImportItem.applyUploaded(loaded, Map.of(map, "a".repeat(32), chart, "b".repeat(32)));
        ImportItem.RequestParts parts = ImportItem.requestParts(loaded);
        assertEquals("a".repeat(32), parts.questions().get(0).options().get(1).imageId());
        assertEquals("b".repeat(32), parts.questions().get(0).imageId());
        assertEquals(List.of("b".repeat(32)), parts.groupImageIds());
    }

    @Test
    void readsVersion3DraftsWithoutImages() throws Exception {
        Path file = dir.resolve("v3.json");
        Files.writeString(file, """
                {"format":"exam-battle-import-draft","version":3,"fileName":"x.pdf","createdAt":"2026-11-01T00:00:00Z",
                 "pages":[],"groups":[{"passage":"文章","firstNumber":1,"lastNumber":1,"sourcePage":1,"warnings":[]}],
                 "items":[{"include":true,"imported":false,"number":1,"sourcePage":1,"warnings":[],"type":"SINGLE_CHOICE",
                           "content":"題目","explanation":"","options":[{"content":"甲","correct":true},{"content":"乙","correct":false}],
                           "trueFalseAnswer":null,"group":0,"unsupported":false}]}""");

        List<ImportItem> items = ImportDraftFile.items(ImportDraftFile.load(file));

        assertEquals(1, items.size());
        assertEquals(null, items.get(0).draft().image());
        assertEquals(null, items.get(0).draft().options().get(0).image());
        assertEquals(null, items.get(0).group().image());
        assertTrue(ImportItem.localImages(items).isEmpty());
        assertEquals(ImportItem.Status.READY, items.get(0).status());
    }
}
