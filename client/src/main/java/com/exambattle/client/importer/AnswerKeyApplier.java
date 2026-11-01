package com.exambattle.client.importer;

import com.exambattle.client.gui.model.QuestionDraft;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.ocr.AnswerKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 依題號把答案卷套用到校正中的題目（見 DECISIONS.md D-030）。
 * 只套用能明確對應的答案：題型未選、字母超出選項數、單選題卻有多個答案等情況一律不套用並列出原因，不猜測。
 * 已匯入的題目不會被修改。
 */
public final class AnswerKeyApplier {

    /** 套用結果。problems 為無法套用的原因（每題一行）。 */
    public record Summary(int applied, List<Integer> notFound, List<String> problems) {
    }

    private AnswerKeyApplier() {
    }

    public static Summary apply(List<ImportItem> items, Map<Integer, AnswerKey> answers) {
        int applied = 0;
        List<Integer> notFound = new ArrayList<>();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<Integer, AnswerKey> entry : answers.entrySet()) {
            int number = entry.getKey();
            List<ImportItem> matches = items.stream()
                    .filter(i -> !i.imported() && Integer.valueOf(number).equals(i.number()))
                    .toList();
            if (matches.isEmpty()) {
                notFound.add(number);
                continue;
            }
            if (matches.size() > 1) {
                problems.add("第 " + number + " 題：有 " + matches.size() + " 題使用相同題號，請先修正題號");
                continue;
            }
            Optional<String> problem = applyOne(matches.get(0), entry.getValue());
            if (problem.isPresent()) {
                problems.add("第 " + number + " 題：" + problem.get());
            } else {
                applied++;
            }
        }
        return new Summary(applied, List.copyOf(notFound), List.copyOf(problems));
    }

    private static Optional<String> applyOne(ImportItem item, AnswerKey key) {
        QuestionDraft d = item.draft();
        if (d.type() == null) {
            return Optional.of("尚未選擇題型，答案「" + key.token() + "」未套用");
        }
        if (d.type() == QuestionType.TRUE_FALSE) {
            Optional<Boolean> tf = key.trueFalse();
            if (tf.isEmpty()) {
                return Optional.of("是非題的答案「" + key.token() + "」不是對／錯記號");
            }
            item.setDraft(new QuestionDraft(d.type(), d.content(), d.explanation(), d.options(), tf.get()));
            return Optional.empty();
        }
        Optional<Set<Character>> letters = key.letters();
        if (letters.isEmpty()) {
            return Optional.of("選擇題的答案「" + key.token() + "」不是選項字母");
        }
        if (d.type() == QuestionType.SINGLE_CHOICE && letters.get().size() > 1) {
            return Optional.of("單選題卻有多個答案「" + key.token() + "」，請確認題型");
        }
        for (char c : letters.get()) {
            if (c - 'A' >= d.options().size()) {
                return Optional.of("答案「" + c + "」超出選項數（共 " + d.options().size() + " 個選項）");
            }
        }
        List<QuestionDraft.Option> options = new ArrayList<>();
        for (int i = 0; i < d.options().size(); i++) {
            options.add(new QuestionDraft.Option(d.options().get(i).content(), letters.get().contains((char) ('A' + i))));
        }
        item.setDraft(new QuestionDraft(d.type(), d.content(), d.explanation(), options, d.trueFalseAnswer()));
        return Optional.empty();
    }
}
