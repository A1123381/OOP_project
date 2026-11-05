package com.exambattle.client.importer;

import com.exambattle.ocr.DraftQuestion;
import com.exambattle.ocr.PageText;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 匯入草稿檔（JSON），存在使用者選擇的本機位置（見 DECISIONS.md D-027、D-033）。
 * 只包含原檔名（不含路徑）、各頁文字與校正中的題目；不包含原始檔案或頁面圖片。
 *
 * <p>v1（第六階段）存切題結果 {@code questions}；v2（第七階段）存校正內容 {@code items}；
 * v3 加上題組 {@code groups}（items 以位置指向題組）；v4 加上題目、選項與題組文章的圖片（Base64，D-043）。
 * 讀取舊版時自動轉換。
 */
public final class ImportDraftFile {

    public static final String FORMAT = "exam-battle-import-draft";
    public static final int VERSION = 4;
    public static final String EXTENSION = "ebdraft.json";

    /** 草稿內容。fileName 只有檔名；questions 只出現在 v1 檔案中；groups 從 v3 開始。 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ImportDraft(String format, int version, String fileName, String createdAt,
                              List<PageText> pages, List<DraftQuestion> questions, List<ImportItem.Data> items,
                              List<ImportGroup.Data> groups) {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ImportDraftFile() {
    }

    /** 建立 v3 草稿；sourceName 若含路徑會只保留檔名。 */
    public static ImportDraft create(String sourceName, List<PageText> pages, List<ImportItem> items) {
        String fileName = Path.of(sourceName).getFileName().toString();
        List<ImportGroup> groups = ImportItem.groupsOf(items);
        List<ImportItem.Data> data = items.stream()
                .map(i -> i.toData(i.group() == null ? null : ImportItem.indexOf(groups, i.group())))
                .toList();
        return new ImportDraft(FORMAT, VERSION, fileName, Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
                List.copyOf(pages), null, data, groups.stream().map(ImportGroup::toData).toList());
    }

    public static void save(Path target, ImportDraft draft) throws IOException {
        Files.writeString(target, MAPPER.writeValueAsString(draft));
    }

    /** 讀取草稿；v1 檔案會把切題結果轉成校正項目。 */
    public static ImportDraft load(Path source) throws IOException {
        ImportDraft draft;
        try {
            draft = MAPPER.readValue(Files.readString(source), ImportDraft.class);
        } catch (IOException e) {
            throw new IOException("不是有效的匯入草稿檔：" + source.getFileName(), e);
        }
        if (!FORMAT.equals(draft.format())) {
            throw new IOException("不是 Exam Battle 的匯入草稿檔：" + source.getFileName());
        }
        if (draft.version() > VERSION) {
            throw new IOException("草稿檔版本 " + draft.version() + " 比目前程式新，請更新程式後再開啟");
        }
        List<ImportItem.Data> items = draft.items();
        if (items == null) {
            List<DraftQuestion> questions = draft.questions() == null ? List.of() : draft.questions();
            items = questions.stream().map(q -> ImportItem.from(q).toData()).toList();
        }
        return new ImportDraft(FORMAT, VERSION, draft.fileName(), draft.createdAt(),
                draft.pages() == null ? List.of() : draft.pages(), null, items,
                draft.groups() == null ? List.of() : draft.groups());
    }

    /** 草稿中的校正項目；同組的題目共用同一個 {@link ImportGroup}。 */
    public static List<ImportItem> items(ImportDraft draft) {
        List<ImportGroup> groups = draft.groups().stream().map(ImportGroup::fromData).toList();
        return draft.items().stream().map(d -> ImportItem.fromData(d, groups)).toList();
    }
}
