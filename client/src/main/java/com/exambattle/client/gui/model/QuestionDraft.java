package com.exambattle.client.gui.model;

import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.bank.OptionData;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * 題目表單的內容。檢查規則與伺服器的 BankValidator 相同，只為了提早提示；最終以伺服器為準。
 * 允許「答案未設定」（不可推測答案），但會在表單上提醒。
 *
 * @param options         選擇題的選項；是非題忽略
 * @param trueFalseAnswer 是非題答案；null 表示未設定
 * @param image           題幹圖片（D-043）；null 表示沒有
 */
public record QuestionDraft(QuestionType type, String content, String explanation,
                            List<Option> options, Boolean trueFalseAnswer, DraftImage image) {

    /** 表單上的一個選項；image 為選項圖片，有圖片時文字可以空白。 */
    public record Option(String content, boolean correct, DraftImage image) {

        public Option(String content, boolean correct) {
            this(content, correct, null);
        }

        public Option withCorrect(boolean value) {
            return new Option(content, value, image);
        }

        public Option withImage(DraftImage value) {
            return new Option(content, correct, value);
        }
    }

    /** 沒有題幹圖片。 */
    public QuestionDraft(QuestionType type, String content, String explanation, List<Option> options,
                         Boolean trueFalseAnswer) {
        this(type, content, explanation, options, trueFalseAnswer, null);
    }

    public static QuestionDraft empty(QuestionType type) {
        List<Option> options = type == QuestionType.TRUE_FALSE
                ? List.of()
                : List.of(new Option("", false), new Option("", false), new Option("", false), new Option("", false));
        return new QuestionDraft(type, "", "", options, null);
    }

    /** 由伺服器的題目建立表單（編輯時使用；建立者一定拿得到答案）。 */
    public static QuestionDraft from(QuestionData q) {
        List<Option> options = q.options().stream()
                .map(o -> new Option(o.content(), Boolean.TRUE.equals(o.correct()),
                        o.imageId() == null ? null : DraftImage.uploaded(o.imageId())))
                .toList();
        return new QuestionDraft(q.type(), q.content(), q.explanation() == null ? "" : q.explanation(),
                options, q.trueFalseAnswer(), q.imageId() == null ? null : DraftImage.uploaded(q.imageId()));
    }

    public QuestionDraft withOptions(List<Option> value) {
        return new QuestionDraft(type, content, explanation, value, trueFalseAnswer, image);
    }

    public QuestionDraft withTrueFalse(Boolean value) {
        return new QuestionDraft(type, content, explanation, options, value, image);
    }

    public QuestionDraft withImage(DraftImage value) {
        return new QuestionDraft(type, content, explanation, options, trueFalseAnswer, value);
    }

    public boolean isChoice() {
        return type != QuestionType.TRUE_FALSE;
    }

    public boolean answerSet() {
        return isChoice() ? options.stream().anyMatch(Option::correct) : trueFalseAnswer != null;
    }

    /** 還沒上傳的圖片（題幹與選項）。 */
    public List<DraftImage> pendingImages() {
        List<DraftImage> pending = new ArrayList<>();
        if (image != null && image.pending()) {
            pending.add(image);
        }
        if (isChoice()) {
            options.stream().map(Option::image).filter(i -> i != null && i.pending()).forEach(pending::add);
        }
        return pending;
    }

    /** 把每張圖片換成 mapper 的結果（例如上傳後帶 id 的版本）。 */
    public QuestionDraft mapImages(UnaryOperator<DraftImage> mapper) {
        List<Option> mapped = options.stream()
                .map(o -> o.image() == null ? o : o.withImage(mapper.apply(o.image())))
                .toList();
        return new QuestionDraft(type, content, explanation, mapped, trueFalseAnswer,
                image == null ? null : mapper.apply(image));
    }

    /** 回傳第一個錯誤訊息；沒有錯誤時為 empty。 */
    public Optional<String> validate() {
        if (type == null) {
            return Optional.of("請選擇題型");
        }
        String text = content == null ? "" : content.strip();
        if (text.isEmpty() && image == null) {
            return Optional.of("請輸入題目內容或加入題目圖片");
        }
        if (text.length() > Protocol.MAX_QUESTION_CHARS) {
            return Optional.of("題目內容不可超過 " + Protocol.MAX_QUESTION_CHARS + " 字元");
        }
        if (explanation != null && explanation.strip().length() > Protocol.MAX_EXPLANATION_CHARS) {
            return Optional.of("解說不可超過 " + Protocol.MAX_EXPLANATION_CHARS + " 字元");
        }
        if (!isChoice()) {
            return Optional.empty();
        }
        if (options.size() < Protocol.MIN_OPTIONS || options.size() > Protocol.MAX_OPTIONS) {
            return Optional.of("選擇題需要 " + Protocol.MIN_OPTIONS + "–" + Protocol.MAX_OPTIONS + " 個選項");
        }
        for (int i = 0; i < options.size(); i++) {
            String option = options.get(i).content() == null ? "" : options.get(i).content().strip();
            if (option.isEmpty() && options.get(i).image() == null) {
                return Optional.of("第 " + (i + 1) + " 個選項不可空白（輸入文字或加入圖片）");
            }
            if (option.length() > Protocol.MAX_OPTION_CHARS) {
                return Optional.of("第 " + (i + 1) + " 個選項不可超過 " + Protocol.MAX_OPTION_CHARS + " 字元");
            }
        }
        if (type == QuestionType.SINGLE_CHOICE && options.stream().filter(Option::correct).count() > 1) {
            return Optional.of("單選題只能有一個正確答案");
        }
        return Optional.empty();
    }

    /**
     * 轉成送給伺服器的題目；呼叫前須先通過 {@link #validate()}，並已上傳全部圖片（{@link #pendingImages()} 為空）。
     */
    public QuestionData toData() {
        if (!pendingImages().isEmpty()) {
            throw new IllegalStateException("還有圖片沒有上傳");
        }
        List<OptionData> data = new ArrayList<>();
        if (isChoice()) {
            for (Option o : options) {
                data.add(new OptionData(null, o.content() == null ? "" : o.content().strip(), o.correct(),
                        o.image() == null ? null : o.image().imageId()));
            }
        }
        String exp = explanation == null || explanation.isBlank() ? null : explanation.strip();
        return new QuestionData(null, type, content == null ? "" : content.strip(), exp, data,
                isChoice() ? null : trueFalseAnswer, false, null, null, image == null ? null : image.imageId(), null);
    }
}
