package com.exambattle.client.importer;

import com.exambattle.common.protocol.Protocol;
import com.exambattle.ocr.DraftGroup;

import java.util.List;
import java.util.Optional;

/**
 * 校正中的題組（閱讀測驗、克漏字等多題共用一篇文章，見 DECISIONS.md D-034）。
 * 同組的 {@link ImportItem} 指向同一個物件，所以修改文章時同組題目一起改變。不依賴 JavaFX。
 */
public final class ImportGroup {

    /** 存進草稿檔的內容。 */
    public record Data(String passage, Integer firstNumber, Integer lastNumber, int sourcePage, List<String> warnings) {
    }

    private String passage;
    private final Integer firstNumber;
    private final Integer lastNumber;
    private final int sourcePage;
    private final List<String> warnings;

    private ImportGroup(String passage, Integer firstNumber, Integer lastNumber, int sourcePage, List<String> warnings) {
        this.passage = passage == null ? "" : passage;
        this.firstNumber = firstNumber;
        this.lastNumber = lastNumber;
        this.sourcePage = sourcePage;
        this.warnings = List.copyOf(warnings);
    }

    public static ImportGroup from(DraftGroup g) {
        return new ImportGroup(g.passage(), g.firstNumber(), g.lastNumber(), g.sourcePage(), g.warnings());
    }

    /** 使用者在校正畫面新增的題組。 */
    public static ImportGroup manual(String passage, Integer firstNumber, Integer lastNumber, int sourcePage) {
        return new ImportGroup(passage, firstNumber, lastNumber, sourcePage, List.of());
    }

    public static ImportGroup fromData(Data d) {
        return new ImportGroup(d.passage(), d.firstNumber(), d.lastNumber(), d.sourcePage(),
                d.warnings() == null ? List.of() : d.warnings());
    }

    public Data toData() {
        return new Data(passage, firstNumber, lastNumber, sourcePage, warnings);
    }

    /** 清單上的標示，例如「題組 11–15」。 */
    public String label() {
        if (firstNumber == null || lastNumber == null) {
            return "題組";
        }
        return "題組 " + firstNumber + "–" + lastNumber;
    }

    /** 文章不符合題庫規則時的原因。 */
    public Optional<String> problem() {
        String text = passage.strip();
        if (text.isEmpty()) {
            return Optional.of("題組文章不可空白");
        }
        if (text.length() > Protocol.MAX_PASSAGE_CHARS) {
            return Optional.of("題組文章不可超過 " + Protocol.MAX_PASSAGE_CHARS + " 字元");
        }
        return Optional.empty();
    }

    public String passage() {
        return passage;
    }

    public void setPassage(String passage) {
        this.passage = passage == null ? "" : passage;
    }

    public int sourcePage() {
        return sourcePage;
    }

    /** 切題時產生的提醒（例如找不到空格）。 */
    public List<String> warnings() {
        return warnings;
    }
}
