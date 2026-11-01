package com.exambattle.client.importer;

import com.exambattle.client.gui.model.QuestionDraft;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.ocr.DraftQuestion;

import java.util.List;
import java.util.Optional;

/**
 * 校正中的一題（第七階段）。內容沿用題庫的 {@link QuestionDraft}，所以驗證規則與題庫一致。
 * 不依賴 JavaFX，可單元測試。
 */
public final class ImportItem {

    /** 匯入前的狀態。 */
    public enum Status {
        /** 格式正確、已設定答案。 */
        READY,
        /** 格式正確，但尚未設定答案（可以匯入，會提醒）。 */
        NO_ANSWER,
        /** 題型未選或格式錯誤，修正前不能匯入。 */
        NEEDS_FIX,
        /** 已經匯入題庫。 */
        IMPORTED
    }

    /** 存進草稿檔的內容。 */
    public record Data(boolean include, boolean imported, Integer number, int sourcePage, List<String> warnings,
                       QuestionType type, String content, String explanation, List<QuestionDraft.Option> options,
                       Boolean trueFalseAnswer) {
    }

    private boolean include;
    private boolean imported;
    private Integer number;
    private final int sourcePage;
    private final List<String> warnings;
    private QuestionDraft draft;

    private ImportItem(boolean include, boolean imported, Integer number, int sourcePage, List<String> warnings,
                       QuestionDraft draft) {
        this.include = include;
        this.imported = imported;
        this.number = number;
        this.sourcePage = sourcePage;
        this.warnings = List.copyOf(warnings);
        this.draft = draft;
    }

    /** 由切題結果建立；答案一律未設定（不推測答案）。 */
    public static ImportItem from(DraftQuestion d) {
        List<QuestionDraft.Option> options = d.options().stream().map(o -> new QuestionDraft.Option(o, false)).toList();
        QuestionType type = d.guessedType();
        if (type == QuestionType.TRUE_FALSE && !options.isEmpty()) {
            type = null; // 是非題不該有選項，交給使用者決定
        }
        return new ImportItem(true, false, d.number(), d.sourcePage(), d.warnings(),
                new QuestionDraft(type, d.content(), "", options, null));
    }

    /** 使用者手動新增的空白題目。 */
    public static ImportItem blank(Integer number, int sourcePage) {
        return new ImportItem(true, false, number, sourcePage, List.of(), QuestionDraft.empty(QuestionType.SINGLE_CHOICE));
    }

    public static ImportItem fromData(Data d) {
        List<QuestionDraft.Option> options = d.options() == null ? List.of() : d.options();
        return new ImportItem(d.include(), d.imported(), d.number(), d.sourcePage(),
                d.warnings() == null ? List.of() : d.warnings(),
                new QuestionDraft(d.type(), nullToEmpty(d.content()), nullToEmpty(d.explanation()), options,
                        d.trueFalseAnswer()));
    }

    public Data toData() {
        return new Data(include, imported, number, sourcePage, warnings, draft.type(), draft.content(),
                draft.explanation(), draft.options(), draft.trueFalseAnswer());
    }

    public Status status() {
        if (imported) {
            return Status.IMPORTED;
        }
        if (draft.validate().isPresent()) {
            return Status.NEEDS_FIX;
        }
        return draft.answerSet() ? Status.READY : Status.NO_ANSWER;
    }

    /** 驗證錯誤；沒有錯誤時為 empty。 */
    public Optional<String> problem() {
        return imported ? Optional.empty() : draft.validate();
    }

    /** 已勾選且尚未匯入。 */
    public boolean selected() {
        return include && !imported;
    }

    public boolean include() {
        return include;
    }

    public void setInclude(boolean include) {
        this.include = include;
    }

    public boolean imported() {
        return imported;
    }

    /** 匯入成功後呼叫：標示已匯入並取消勾選，避免重複匯入。 */
    public void markImported() {
        imported = true;
        include = false;
    }

    public Integer number() {
        return number;
    }

    public void setNumber(Integer number) {
        this.number = number;
    }

    public int sourcePage() {
        return sourcePage;
    }

    /** 切題時產生的原始警示（供參考，不影響能否匯入）。 */
    public List<String> warnings() {
        return warnings;
    }

    public QuestionDraft draft() {
        return draft;
    }

    public void setDraft(QuestionDraft draft) {
        this.draft = draft;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
