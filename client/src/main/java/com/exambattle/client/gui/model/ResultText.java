package com.exambattle.client.gui.model;

import com.exambattle.common.protocol.bank.OptionData;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.practice.AnswerData;
import com.exambattle.common.protocol.practice.PracticeMode;
import com.exambattle.common.protocol.practice.RevealMode;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** 練習結果、紀錄、錯題本共用的顯示文字。不依賴 JavaFX，可單元測試。 */
public final class ResultText {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private ResultText() {
    }

    /** 選項代號：A、B、C…。 */
    public static String letter(int position) {
        return String.valueOf((char) ('A' + position));
    }

    /** 選項 id 轉成代號，例如「A、C」；空的時候為「未作答」。 */
    public static String letters(List<OptionData> options, Collection<Long> optionIds) {
        List<String> picked = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            if (optionIds.contains(options.get(i).id())) {
                picked.add(letter(i));
            }
        }
        return picked.isEmpty() ? "未作答" : String.join("、", picked);
    }

    public static String trueFalse(Boolean answer) {
        return answer == null ? "未作答" : answer ? "對" : "錯";
    }

    /** 題目的正確答案（題目快照中含答案）。 */
    public static String correctAnswer(QuestionData q) {
        if (q.type() == QuestionType.TRUE_FALSE) {
            return q.trueFalseAnswer() == null ? "未設定" : trueFalse(q.trueFalseAnswer());
        }
        List<Long> ids = q.options().stream().filter(o -> Boolean.TRUE.equals(o.correct())).map(OptionData::id).toList();
        return ids.isEmpty() ? "未設定" : letters(q.options(), ids);
    }

    public static String yourAnswer(QuestionData q, AnswerData a) {
        if (q.type() == QuestionType.TRUE_FALSE) {
            return trueFalse(a == null ? null : a.trueFalseAnswer());
        }
        return letters(q.options(), a == null || a.selectedOptionIds() == null ? List.of() : a.selectedOptionIds());
    }

    /** 單題得分（0–1），例如「1」「0.6」「0」。 */
    public static String questionScore(double score) {
        if (score == Math.rint(score)) {
            return String.valueOf((long) score);
        }
        return String.format(Locale.ROOT, "%.2f", score).replaceAll("0+$", "");
    }

    /** 100 分制分數，例如「53.3」「100」。 */
    public static String score(double score) {
        return score == Math.rint(score) ? String.valueOf((long) score) : String.format(Locale.ROOT, "%.1f", score);
    }

    /** 比例（0–1）轉百分比，例如「33.3%」。 */
    public static String percent(double ratio) {
        return score(Math.round(ratio * 1000.0) / 10.0) + "%";
    }

    /** 作答時間，例如「45 秒」「3 分 05 秒」「1 小時 02 分」。 */
    public static String duration(long millis) {
        long seconds = Math.max(0, millis) / 1000;
        if (seconds < 60) {
            return seconds + " 秒";
        }
        if (seconds < 3600) {
            return seconds / 60 + " 分 " + String.format("%02d", seconds % 60) + " 秒";
        }
        return seconds / 3600 + " 小時 " + String.format("%02d", seconds % 3600 / 60) + " 分";
    }

    public static String dateTime(String iso, ZoneId zone) {
        try {
            return DATE_TIME.format(Instant.parse(iso).atZone(zone));
        } catch (RuntimeException e) {
            return "";
        }
    }

    public static String mode(PracticeMode mode) {
        return switch (mode) {
            case FULL_EXAM -> "完整考卷";
            case RANDOM -> "隨機抽題";
            case WRONG_REVIEW -> "錯題複習";
        };
    }

    public static String reveal(RevealMode mode) {
        return switch (mode) {
            case AFTER_SUBMIT -> "考試模式";
            case IMMEDIATE -> "練習模式";
        };
    }
}
