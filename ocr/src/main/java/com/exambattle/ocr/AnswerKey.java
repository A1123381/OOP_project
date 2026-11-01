package com.exambattle.ocr;

import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * 答案卷上一題的答案記號（已正規化）。同一個記號可能有兩種解讀，例如「F」可以是選項 F 或「錯」，
 * 因此只保存原始記號，套用到已知題型的題目時再決定（見 DECISIONS.md D-030）。
 */
public record AnswerKey(String token) {

    private static final String TRUE_MARKS = "○OoT✓√對";
    private static final String FALSE_MARKS = "×XxF✗錯";

    /** 解讀成選項字母（A–J）；含其他字元時為 empty。 */
    public Optional<Set<Character>> letters() {
        Set<Character> letters = new TreeSet<>();
        for (char c : token.toCharArray()) {
            if (c >= 'A' && c <= 'J') {
                letters.add(c);
            } else if (c != ',' && c != '、' && c != ' ') {
                return Optional.empty();
            }
        }
        return letters.isEmpty() ? Optional.empty() : Optional.of(letters);
    }

    /** 解讀成是非答案；不是單一是非記號時為 empty。 */
    public Optional<Boolean> trueFalse() {
        if (token.length() != 1) {
            return Optional.empty();
        }
        if (TRUE_MARKS.indexOf(token.charAt(0)) >= 0) {
            return Optional.of(Boolean.TRUE);
        }
        if (FALSE_MARKS.indexOf(token.charAt(0)) >= 0) {
            return Optional.of(Boolean.FALSE);
        }
        return Optional.empty();
    }
}
