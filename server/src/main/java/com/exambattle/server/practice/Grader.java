package com.exambattle.server.practice;

import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.practice.AnswerData;
import com.exambattle.server.dao.BankRecords.OptionRecord;
import com.exambattle.server.dao.BankRecords.QuestionRecord;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 評分規則（見 DECISIONS.md D-023），一律由伺服器計算：
 * <ul>
 *   <li>單選、是非：答對 1 分，否則 0 分</li>
 *   <li>多選（學測規則）：n 個選項中有 k 個與正確答案不一致，得 max(0, (n − 2k) / n) 分；未作答 0 分</li>
 *   <li>只有得 1 分（全對）才算答對</li>
 * </ul>
 */
public final class Grader {

    private Grader() {
    }

    /** 本題得分（0–1）。不屬於本題的選項 id 會被忽略。 */
    public static double score(QuestionRecord question, AnswerData answer) {
        if (question.type() == QuestionType.TRUE_FALSE) {
            Boolean given = answer == null ? null : answer.trueFalseAnswer();
            return given != null && given.equals(question.trueFalseAnswer()) ? 1.0 : 0.0;
        }
        Set<Long> selected = selectedIn(question, answer);
        if (selected.isEmpty()) {
            return 0.0;
        }
        Set<Long> correct = correctIds(question);
        if (question.type() == QuestionType.SINGLE_CHOICE) {
            return selected.equals(correct) ? 1.0 : 0.0;
        }
        int n = question.options().size();
        long k = question.options().stream()
                .filter(o -> selected.contains(o.id()) != o.correct())
                .count();
        return Math.max(0.0, (n - 2.0 * k) / n);
    }

    public static boolean isCorrect(double score) {
        return score >= 1.0;
    }

    /** 總分：各題得分平均後換算成 100 分制，四捨五入到小數 1 位。 */
    public static double total(List<Double> scores) {
        if (scores.isEmpty()) {
            return 0.0;
        }
        double sum = scores.stream().mapToDouble(Double::doubleValue).sum();
        return Math.round(sum / scores.size() * 1000.0) / 10.0;
    }

    public static Set<Long> correctIds(QuestionRecord question) {
        Set<Long> ids = new HashSet<>();
        for (OptionRecord o : question.options()) {
            if (o.correct()) {
                ids.add(o.id());
            }
        }
        return ids;
    }

    private static Set<Long> selectedIn(QuestionRecord question, AnswerData answer) {
        Set<Long> selected = new HashSet<>();
        if (answer == null || answer.selectedOptionIds() == null) {
            return selected;
        }
        Set<Long> valid = new HashSet<>();
        question.options().forEach(o -> valid.add(o.id()));
        for (Long id : answer.selectedOptionIds()) {
            if (id != null && valid.contains(id)) {
                selected.add(id);
            }
        }
        return selected;
    }
}
