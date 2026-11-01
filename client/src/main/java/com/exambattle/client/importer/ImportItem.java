package com.exambattle.client.importer;

import com.exambattle.client.gui.model.QuestionDraft;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.ocr.DraftGroup;
import com.exambattle.ocr.DraftQuestion;
import com.exambattle.ocr.SplitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 校正中的一題（第七階段）。內容沿用題庫的 {@link QuestionDraft}，所以驗證規則與題庫一致。
 * 可以屬於一個 {@link ImportGroup}（題組，同組題目共用文章）。不依賴 JavaFX，可單元測試。
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

    /**
     * 存進草稿檔的內容。group 是所屬題組在草稿 groups 中的位置（v3 起），null 表示不屬於題組；
     * unsupported 表示切題時判斷為非選擇題。
     */
    public record Data(boolean include, boolean imported, Integer number, int sourcePage, List<String> warnings,
                       QuestionType type, String content, String explanation, List<QuestionDraft.Option> options,
                       Boolean trueFalseAnswer, Integer group, boolean unsupported) {
    }

    /** 匯入請求的題目部分：questions 與 groupIndexes 一一對應（見 ImportRequest）。 */
    public record RequestParts(List<QuestionData> questions, List<String> groups, List<Integer> groupIndexes) {
    }

    private boolean include;
    private boolean imported;
    private Integer number;
    private final int sourcePage;
    private final List<String> warnings;
    private final boolean unsupported;
    private QuestionDraft draft;
    private ImportGroup group;

    private ImportItem(boolean include, boolean imported, Integer number, int sourcePage, List<String> warnings,
                       QuestionDraft draft, ImportGroup group, boolean unsupported) {
        this.include = include;
        this.imported = imported;
        this.number = number;
        this.sourcePage = sourcePage;
        this.warnings = List.copyOf(warnings);
        this.draft = draft;
        this.group = group;
        this.unsupported = unsupported;
    }

    /** 由切題結果建立；答案一律未設定（不推測答案）。非選擇題預設不勾選。 */
    public static ImportItem from(DraftQuestion d) {
        return from(d, null);
    }

    /** 由切題結果建立，並指定所屬題組。 */
    public static ImportItem from(DraftQuestion d, ImportGroup group) {
        List<QuestionDraft.Option> options = d.options().stream().map(o -> new QuestionDraft.Option(o, false)).toList();
        QuestionType type = d.guessedType();
        if (type == QuestionType.TRUE_FALSE && !options.isEmpty()) {
            type = null; // 是非題不該有選項，交給使用者決定
        }
        return new ImportItem(!d.unsupported(), false, d.number(), d.sourcePage(), d.warnings(),
                new QuestionDraft(type, d.content(), "", options, null), group, d.unsupported());
    }

    /** 整份切題結果：同一題組的題目共用同一個 {@link ImportGroup}。 */
    public static List<ImportItem> fromSplit(SplitResult result) {
        List<ImportGroup> groups = result.groups().stream().map(ImportGroup::from).toList();
        return result.questions().stream()
                .map(q -> from(q, q.groupIndex() == null ? null : groups.get(q.groupIndex())))
                .toList();
    }

    /** 使用者手動新增的空白題目。 */
    public static ImportItem blank(Integer number, int sourcePage) {
        return new ImportItem(true, false, number, sourcePage, List.of(), QuestionDraft.empty(QuestionType.SINGLE_CHOICE),
                null, false);
    }

    /** 由草稿還原；groups 是草稿中的題組（Data.group 指向其中一個）。 */
    public static ImportItem fromData(Data d, List<ImportGroup> groups) {
        List<QuestionDraft.Option> options = d.options() == null ? List.of() : d.options();
        ImportGroup group = d.group() == null || d.group() < 0 || d.group() >= groups.size() ? null : groups.get(d.group());
        return new ImportItem(d.include(), d.imported(), d.number(), d.sourcePage(),
                d.warnings() == null ? List.of() : d.warnings(),
                new QuestionDraft(d.type(), nullToEmpty(d.content()), nullToEmpty(d.explanation()), options,
                        d.trueFalseAnswer()), group, d.unsupported());
    }

    public static ImportItem fromData(Data d) {
        return fromData(d, List.of());
    }

    /** groupIndex：所屬題組在草稿 groups 中的位置。 */
    public Data toData(Integer groupIndex) {
        return new Data(include, imported, number, sourcePage, warnings, draft.type(), draft.content(),
                draft.explanation(), draft.options(), draft.trueFalseAnswer(), groupIndex, unsupported);
    }

    public Data toData() {
        return toData(null);
    }

    /** 這些題目用到的題組，依第一次出現的順序。 */
    public static List<ImportGroup> groupsOf(List<ImportItem> items) {
        List<ImportGroup> groups = new ArrayList<>();
        for (ImportItem item : items) {
            if (item.group != null && groups.stream().noneMatch(g -> g == item.group)) {
                groups.add(item.group);
            }
        }
        return groups;
    }

    /** 轉成匯入請求的題目、題組文章與每題的題組位置；只包含這些題目用到的題組。 */
    public static RequestParts requestParts(List<ImportItem> items) {
        List<ImportGroup> groups = groupsOf(items);
        List<QuestionData> questions = new ArrayList<>();
        List<Integer> indexes = new ArrayList<>();
        for (ImportItem item : items) {
            questions.add(item.draft().toData());
            indexes.add(item.group == null ? null : indexOf(groups, item.group));
        }
        return new RequestParts(questions, groups.stream().map(g -> g.passage().strip()).toList(), indexes);
    }

    static int indexOf(List<ImportGroup> groups, ImportGroup group) {
        for (int i = 0; i < groups.size(); i++) {
            if (groups.get(i) == group) {
                return i;
            }
        }
        return -1;
    }

    public Status status() {
        if (imported) {
            return Status.IMPORTED;
        }
        if (problem().isPresent()) {
            return Status.NEEDS_FIX;
        }
        return draft.answerSet() ? Status.READY : Status.NO_ANSWER;
    }

    /** 驗證錯誤（題目本身或所屬題組的文章）；沒有錯誤時為 empty。 */
    public Optional<String> problem() {
        if (imported) {
            return Optional.empty();
        }
        Optional<String> own = draft.validate();
        if (own.isPresent() || group == null) {
            return own;
        }
        return group.problem();
    }

    /** 切題時判斷為非選擇題（系統不支援，預設不勾選）。 */
    public boolean unsupported() {
        return unsupported;
    }

    /** 所屬題組；null 表示不屬於題組。 */
    public ImportGroup group() {
        return group;
    }

    public void setGroup(ImportGroup group) {
        this.group = group;
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
