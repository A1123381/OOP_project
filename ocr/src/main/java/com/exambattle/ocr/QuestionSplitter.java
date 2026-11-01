package com.exambattle.ocr;

import com.exambattle.common.protocol.bank.QuestionType;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 依題號與選項格式把擷取出的文字初步切成題目草稿（見 DECISIONS.md D-028）。
 * 只做格式比對，不使用 AI；不推測正確答案。結果一律需要人工校正，可疑之處加上警示。
 *
 * <ul>
 *   <li>題號：{@code 1.}、{@code 1、}、{@code 1)}、{@code (1)}、{@code 第1題}（全形會先轉成半形）。
 *       題號必須比前一題大，避免把「(1)(2)」之類的內文誤判成新題；跳號時加上警示。</li>
 *   <li>選項：{@code (A)}、{@code A.}、{@code A、}、{@code A)}（A–J，可在同一行或分行）。依字母順序接受；有缺字母時加上警示。</li>
 *   <li>OCR 常把選項記號辨識壞（漏掉括號、把 (C) 讀成 (0、(O）。這類「不完整的記號」只有在字母剛好是
 *       下一個預期字母時才接受，並加上警示請使用者確認。</li>
 *   <li>分段標題（例如「二、多選題」）會影響之後題目的題型推測。</li>
 * </ul>
 */
public final class QuestionSplitter {

    static final int MAX_QUESTION_NUMBER = 300;

    private static final Pattern QUESTION_START = Pattern.compile(
            "^\\s*(?:第\\s*(\\d{1,3})\\s*題[.、:：]?|\\(\\s*(\\d{1,3})\\s*\\)|(\\d{1,3})\\s*[.、)](?!\\d))\\s*(.*)$");

    /**
     * 選項記號。完整：(A)～(J)（括號內可小寫）、行首／空白後的 A. A、 A)。
     * 不完整（OCR 常見）：只有左括號的 (A、行首／空白後緊接數字的 A2、被讀成 (0 或 (O 的 (C。
     */
    private static final Pattern OPTION_MARKER = Pattern.compile(
            "(?<![A-Za-z0-9])\\(\\s*([A-Ja-j])\\s*\\)"
                    + "|(?:^|(?<=\\s))([A-J])(?:\\s*[.、](?!\\d)|\\))"
                    + "|(?<![A-Za-z0-9])\\(([A-J])(?![A-Za-z)])"
                    + "|(?:^|(?<=\\s))([A-J])(?=\\d)"
                    + "|(?<![A-Za-z0-9])\\(([O0o])(?![A-Za-z)])",
            Pattern.MULTILINE);

    private enum Section { SINGLE, MULTIPLE, TRUE_FALSE }

    private QuestionSplitter() {
    }

    public static List<DraftQuestion> split(List<PageText> pages) {
        List<Block> blocks = new ArrayList<>();
        Block current = null;
        Section section = null;
        Integer lastNumber = null;

        for (PageText page : pages) {
            for (String rawLine : page.text().split("\\R")) {
                String line = normalize(rawLine);
                Section header = sectionOf(line);
                if (header != null) {
                    section = header;
                    continue;
                }
                Matcher m = QUESTION_START.matcher(line);
                Integer number = m.matches() ? parseNumber(m) : null;
                if (number != null && number > 0 && number <= MAX_QUESTION_NUMBER
                        && (lastNumber == null || number > lastNumber)) {
                    current = new Block(number, page.page(), section, lastNumber);
                    current.lines.add(m.group(4));
                    blocks.add(current);
                    lastNumber = number;
                } else if (current != null && !line.isBlank()) {
                    current.lines.add(line.strip());
                }
            }
        }
        return blocks.stream().map(QuestionSplitter::toDraft).toList();
    }

    private static DraftQuestion toDraft(Block block) {
        String text = String.join("\n", block.lines).strip();
        List<String> warnings = new ArrayList<>();
        if (block.previousNumber != null && block.number != block.previousNumber + 1) {
            warnings.add("題號不連續（前一題是 " + block.previousNumber + "）");
        }

        List<Marker> markers = new ArrayList<>();
        Matcher m = OPTION_MARKER.matcher(text);
        char expected = 'A';
        boolean gap = false;
        boolean damaged = false;
        while (m.find()) {
            boolean complete = m.group(1) != null || m.group(2) != null;
            char c;
            if (m.group(5) != null) {
                c = 'C'; // (0、(O 是 (C 的常見誤判
            } else {
                String letter = complete ? (m.group(1) != null ? m.group(1) : m.group(2))
                        : (m.group(3) != null ? m.group(3) : m.group(4));
                c = Character.toUpperCase(letter.charAt(0));
            }
            if (!complete && c != expected) {
                continue; // 不完整的記號只在剛好是下一個字母時才接受
            }
            if (c < expected) {
                continue; // 字母倒退（例如內文中的 A.）不視為選項
            }
            if (c > expected) {
                gap = true;
            }
            damaged |= !complete;
            markers.add(new Marker(m.start(), m.end()));
            expected = (char) (c + 1);
        }

        String stem;
        List<String> options = new ArrayList<>();
        if (markers.size() >= 2) {
            stem = text.substring(0, markers.get(0).start()).strip();
            for (int i = 0; i < markers.size(); i++) {
                int end = i + 1 < markers.size() ? markers.get(i + 1).start() : text.length();
                options.add(text.substring(markers.get(i).end(), end).replaceAll("\\s+", " ").strip());
            }
            if (gap) {
                warnings.add("選項字母不連續，可能有選項沒有辨識出來");
            }
            if (damaged) {
                warnings.add("選項記號辨識不完整（例如缺少括號），請確認選項切分是否正確");
            }
            if (options.stream().anyMatch(String::isEmpty)) {
                warnings.add("有選項內容是空的");
            }
        } else {
            stem = text;
            if (markers.size() == 1) {
                warnings.add("只找到 1 個選項，請確認選項與題型");
            } else if (block.section != Section.TRUE_FALSE) {
                warnings.add("找不到選項，請確認題型");
            }
        }
        if (stem.codePointCount(0, stem.length()) < 4) {
            warnings.add("題目內容過短，可能辨識不完整");
        }

        QuestionType type;
        if (options.size() >= 2) {
            type = block.section == Section.MULTIPLE ? QuestionType.MULTIPLE_CHOICE : QuestionType.SINGLE_CHOICE;
        } else {
            type = block.section == Section.TRUE_FALSE ? QuestionType.TRUE_FALSE : null;
        }
        return new DraftQuestion(block.number, stem, List.copyOf(options), type, block.page, List.copyOf(warnings));
    }

    /** 「一、單選題」「貳、多選題（每題 4 分）」之類的短行；回傳 null 表示不是分段標題。 */
    private static Section sectionOf(String line) {
        String s = line.strip();
        if (s.isEmpty() || s.length() > 40 || QUESTION_START.matcher(s).matches()) {
            return null;
        }
        if (s.contains("多選") || s.contains("複選")) {
            return Section.MULTIPLE;
        }
        if (s.contains("是非")) {
            return Section.TRUE_FALSE;
        }
        if (s.contains("單選") || s.contains("選擇題")) {
            return Section.SINGLE;
        }
        return null;
    }

    private static Integer parseNumber(Matcher m) {
        for (int g = 1; g <= 3; g++) {
            if (m.group(g) != null) {
                return Integer.parseInt(m.group(g));
            }
        }
        return null;
    }

    /** 全形英數字與常用標點轉半形，方便比對題號與選項。 */
    static String normalize(String line) {
        StringBuilder out = new StringBuilder(line.length());
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c >= '０' && c <= '９' || c >= 'Ａ' && c <= 'Ｚ' || c >= 'ａ' && c <= 'ｚ') {
                out.append((char) (c - 0xFEE0));
            } else if (c == '（') {
                out.append('(');
            } else if (c == '）') {
                out.append(')');
            } else if (c == '．') {
                out.append('.');
            } else if (c == '　') {
                out.append(' ');
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private record Marker(int start, int end) {
    }

    private static final class Block {
        final int number;
        final int page;
        final Section section;
        final Integer previousNumber;
        final List<String> lines = new ArrayList<>();

        Block(int number, int page, Section section, Integer previousNumber) {
            this.number = number;
            this.page = page;
            this.section = section;
            this.previousNumber = previousNumber;
        }
    }
}
