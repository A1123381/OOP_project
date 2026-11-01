package com.exambattle.ocr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析答案卷文字（手動貼上或 OCR 結果），得到「題號 → 答案記號」（見 DECISIONS.md D-030）。
 * 只做格式比對；無法解析的部分列為警告，不猜測。
 *
 * <ul>
 *   <li>逐題：{@code 1.B 2.AC 3.○ 4.×}、{@code 1 (B)}、{@code 1、A,C}，可在同一行或每行一題</li>
 *   <li>表格兩行式：{@code 題號 1 2 3} 與 {@code 答案 B C A}（依位置對應）</li>
 *   <li>{@code 對}、{@code 錯} 必須是獨立的記號，後面不能緊接中文（避免「1. 對公布之試題…」被讀成第 1 題＝對）</li>
 *   <li>{@code 47 ／}：非選擇題，記在 {@link Result#nonChoice()}，不列為警告（見 D-036）</li>
 *   <li>以 {@code ※} 開頭的附註行略過</li>
 * </ul>
 */
public final class AnswerKeyParser {

    /** 解析結果：題號由小到大；nonChoice 是答案卷標成「／」的非選擇題題號。 */
    public record Result(Map<Integer, AnswerKey> answers, List<String> warnings, Set<Integer> nonChoice) {
    }

    private static final String MARK = "[A-J](?:\\s*[,、]?\\s*[A-J])*|[○OoT✓√對×Xx✗錯]";

    private static final Pattern PAIR = Pattern.compile(
            "(?<!\\d)(\\d{1,3})\\s*[.、:：)\\]]?\\s*[(\\[]?\\s*(" + MARK + ")\\s*[)\\]]?(?![A-Za-z\\p{IsHan}])");

    /** 非選擇題：題號後只有「／」或「/」。 */
    private static final Pattern NON_CHOICE = Pattern.compile(
            "(?<![\\d/])(\\d{1,3})\\s*[.、:：)]?\\s*[／/](?![\\w/])");

    private static final Pattern NUMBER_ROW = Pattern.compile("^\\s*(?:題號|題次|題目)\\s*[:：]?\\s*(.+)$");
    private static final Pattern ANSWER_ROW = Pattern.compile("^\\s*(?:答案|解答|正答)\\s*[:：]?\\s*(.+)$");

    private AnswerKeyParser() {
    }

    public static Result parse(String text) {
        Map<Integer, AnswerKey> answers = new TreeMap<>();
        List<String> warnings = new ArrayList<>();
        Set<Integer> nonChoice = new TreeSet<>();
        String[] lines = text == null ? new String[0] : text.split("\\R");

        for (int i = 0; i < lines.length; i++) {
            String line = QuestionSplitter.normalize(lines[i]);
            if (line.strip().startsWith("※")) {
                continue;
            }
            Matcher nc = NON_CHOICE.matcher(line);
            while (nc.find()) {
                nonChoice.add(Integer.parseInt(nc.group(1)));
            }
            line = nc.replaceAll(" ");
            Matcher numbers = NUMBER_ROW.matcher(line);
            if (numbers.matches() && i + 1 < lines.length) {
                Matcher marks = ANSWER_ROW.matcher(QuestionSplitter.normalize(lines[i + 1]));
                if (marks.matches()) {
                    parseTable(numbers.group(1), marks.group(1), answers, warnings);
                    i++;
                    continue;
                }
            }
            parsePairs(line, answers, warnings);
        }
        nonChoice.removeAll(answers.keySet());
        if (answers.isEmpty() && nonChoice.isEmpty() && lines.length > 0 && !String.join("", lines).isBlank()) {
            warnings.add("沒有找到「題號＋答案」格式的內容");
        }
        return new Result(answers, List.copyOf(warnings), java.util.Collections.unmodifiableSet(nonChoice));
    }

    private static void parsePairs(String line, Map<Integer, AnswerKey> answers, List<String> warnings) {
        Matcher m = PAIR.matcher(line);
        StringBuilder leftover = new StringBuilder();
        int last = 0;
        while (m.find()) {
            leftover.append(line, last, m.start()).append(' ');
            last = m.end();
            put(Integer.parseInt(m.group(1)), m.group(2), answers, warnings);
        }
        leftover.append(line.substring(last));
        String rest = leftover.toString().replaceAll("[\\s,、;；。.:：]+", " ").strip();
        if (!rest.isEmpty() && rest.chars().anyMatch(Character::isLetterOrDigit) && !isHeading(rest)) {
            warnings.add("無法解析：" + abbreviate(rest));
        }
    }

    private static void parseTable(String numberRow, String answerRow, Map<Integer, AnswerKey> answers,
                                   List<String> warnings) {
        String[] numbers = numberRow.strip().split("\\s+");
        String[] marks = answerRow.strip().split("\\s+");
        if (numbers.length != marks.length) {
            warnings.add("表格的題號（" + numbers.length + " 個）與答案（" + marks.length + " 個）數量不同，只對應前 "
                    + Math.min(numbers.length, marks.length) + " 個");
        }
        for (int i = 0; i < Math.min(numbers.length, marks.length); i++) {
            if (!numbers[i].matches("\\d{1,3}") || !marks[i].matches(MARK)) {
                warnings.add("無法解析：" + numbers[i] + " → " + marks[i]);
                continue;
            }
            put(Integer.parseInt(numbers[i]), marks[i], answers, warnings);
        }
    }

    private static void put(int number, String mark, Map<Integer, AnswerKey> answers, List<String> warnings) {
        if (number < 1 || number > QuestionSplitter.MAX_QUESTION_NUMBER) {
            warnings.add("題號 " + number + " 超出範圍，已略過");
            return;
        }
        String token = mark.replaceAll("[\\s,、]", "").toUpperCase();
        // 是非記號 o/x 轉大寫後與選項字母不衝突（選項只有 A–J）
        AnswerKey previous = answers.put(number, new AnswerKey(token));
        if (previous != null && !previous.token().equals(token)) {
            warnings.add("第 " + number + " 題出現兩次（" + previous.token() + "、" + token + "），使用後者");
        }
    }

    private static boolean isHeading(String rest) {
        return rest.matches(".*(答案|解答|正答|標準答案|題號|單選|多選|是非|科|學年度|考試).*") && rest.length() <= 40
                && !rest.matches(".*\\d.*[A-J].*");
    }

    private static String abbreviate(String s) {
        return s.length() <= 30 ? s : s.substring(0, 30) + "…";
    }
}
