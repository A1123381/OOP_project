package com.exambattle.client.gui.model;

import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.bank.OptionData;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 題目表單的內容。檢查規則與伺服器的 BankValidator 相同，只為了提早提示；最終以伺服器為準。
 * 允許「答案未設定」（不可推測答案），但會在表單上提醒。
 *
 * @param options         選擇題的選項；是非題忽略
 * @param trueFalseAnswer 是非題答案；null 表示未設定
 */
public record QuestionDraft(QuestionType type, String content, String explanation,
                            List<Option> options, Boolean trueFalseAnswer) {

    /** 表單上的一個選項。 */
    public record Option(String content, boolean correct) {
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
                .map(o -> new Option(o.content(), Boolean.TRUE.equals(o.correct())))
                .toList();
        return new QuestionDraft(q.type(), q.content(), q.explanation() == null ? "" : q.explanation(),
                options, q.trueFalseAnswer());
    }

    public boolean isChoice() {
        return type != QuestionType.TRUE_FALSE;
    }

    public boolean answerSet() {
        return isChoice() ? options.stream().anyMatch(Option::correct) : trueFalseAnswer != null;
    }

    /** 回傳第一個錯誤訊息；沒有錯誤時為 empty。 */
    public Optional<String> validate() {
        if (type == null) {
            return Optional.of("請選擇題型");
        }
        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            return Optional.of("請輸入題目內容");
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
            if (option.isEmpty()) {
                return Optional.of("第 " + (i + 1) + " 個選項不可空白");
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

    /** 轉成送給伺服器的題目；呼叫前須先通過 {@link #validate()}。 */
    public QuestionData toData() {
        List<OptionData> data = new ArrayList<>();
        if (isChoice()) {
            for (Option o : options) {
                data.add(new OptionData(null, o.content().strip(), o.correct()));
            }
        }
        String exp = explanation == null || explanation.isBlank() ? null : explanation.strip();
        return new QuestionData(null, type, content.strip(), exp, data, isChoice() ? null : trueFalseAnswer, false);
    }
}
