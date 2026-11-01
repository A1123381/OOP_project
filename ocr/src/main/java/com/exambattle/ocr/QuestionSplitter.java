package com.exambattle.ocr;

import com.exambattle.common.protocol.bank.QuestionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 依題號與選項格式把擷取出的文字初步切成題目草稿（見 DECISIONS.md D-028、D-035）。
 * 只做格式比對，不使用 AI；不推測正確答案。結果一律需要人工校正，可疑之處加上警示。
 *
 * <ul>
 *   <li>題號：{@code 1.}、{@code 1、}、{@code 1)}、{@code (1)}、{@code 第1題}（全形會先轉成半形）。
 *       題號必須比前一題大，避免把「(1)(2)」之類的內文誤判成新題；跳號時加上警示。</li>
 *   <li>選項：{@code (A)}、{@code A.}、{@code A、}、{@code A)}（A–J，可在同一行或分行）。依字母順序接受；有缺字母時加上警示。</li>
 *   <li>OCR 常把選項記號辨識壞（漏掉括號、把 (C) 讀成 (0、(O）。這類「不完整的記號」只有在字母剛好是
 *       下一個預期字母時才接受，並加上警示請使用者確認。</li>
 *   <li>頁首頁尾（多數頁面重複出現的行、{@code - 1 -}、{@code 第 1 頁}）會先移除。</li>
 *   <li>大題標題（{@code 一、詞彙題}、{@code 第壹部分}，容許字間空白）與 {@code 說明：…} 不併入題目，
 *       並用來推測之後題目的題型；混合題、非選擇題段落的題目標成不支援。</li>
 *   <li>題組標題（{@code 第 11 至 15 題為題組}、{@code 請閱讀下文，回答第 1 至 3 題}、{@code Questions 1-3}）
 *       到第一題之間的文字是題組文章。題組分成三種：閱讀測驗（每題有題幹與選項）、克漏字（題號後直接是選項，
 *       題幹填入「第 N 格」）、共用選項（文意選填、篇章結構：文章後只有一組選項，逐題產生）。</li>
 * </ul>
 */
public final class QuestionSplitter {

    static final int MAX_QUESTION_NUMBER = 300;
    /** 沒有題組標題時，超過這個字數（不含空白）的無題號文字視為可能的題組文章。 */
    static final int LONG_PASSAGE_CHARS = 200;
    static final int MAX_GROUP_SIZE = 30;

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

    /** 共用選項清單的第一行：(A) 或 A. 開頭。 */
    private static final Pattern POOL_START = Pattern.compile("^(?:\\(\\s*A\\s*\\)|A\\s*[.、)])");

    private static final Pattern PAGE_NUMBER = Pattern.compile(
            "^(?:-\\s*\\d{1,3}\\s*-|第\\s*\\d{1,3}\\s*頁(?:\\s*[,，/]?\\s*共\\s*\\d{1,3}\\s*頁)?|共\\s*\\d{1,3}\\s*頁"
                    + "|\\d{1,3}\\s*/\\s*\\d{1,3}|(?i:page)\\s*\\d{1,3}(?:\\s*(?i:of)\\s*\\d{1,3})?)$");

    private static final String CHINESE_ORDINAL = "[壹貳參肆伍陸柒捌玖拾一二三四五六七八九十]{1,3}";
    // 以下三個比對「去掉所有空白」後的行
    private static final Pattern PART_TITLE = Pattern.compile("^第" + CHINESE_ORDINAL + "部分");
    private static final Pattern SECTION_TITLE = Pattern.compile("^" + CHINESE_ORDINAL + "[、.]");
    private static final Pattern SECTION_TITLE_WORDS = Pattern.compile("題|測驗|占|選填|結構|作文|翻譯|譯");
    private static final Pattern INSTRUCTION = Pattern.compile("^說明[:：︰]");
    private static final Pattern ANNOTATION = Pattern.compile("^\\(.{0,20}題.{0,20}\\)$");

    private static final String RANGE_DASH = "(?:至|到|~|-|－|–|—)";
    private static final Pattern GROUP_HEADER_ZH = Pattern.compile(
            "第(\\d{1,3})題?" + RANGE_DASH + "第?(\\d{1,3})題(?:為|是)?題組");
    private static final Pattern GROUP_HEADER_READ = Pattern.compile(
            "^請?閱讀.{0,30}?第(\\d{1,3})題?" + RANGE_DASH + "第?(\\d{1,3})題");
    private static final Pattern GROUP_HEADER_EN = Pattern.compile(
            "^Questions?\\s*(\\d{1,3})\\s*(?:-|–|—|~|to|through)\\s*(\\d{1,3})\\b", Pattern.CASE_INSENSITIVE);

    private static final String UNSUPPORTED_WARNING = "非選擇題（例如填充、簡答、作文），系統只支援單選、多選與是非題；預設不匯入";

    private enum Section { SINGLE, MULTIPLE, TRUE_FALSE, UNSUPPORTED }

    private QuestionSplitter() {
    }

    public static SplitResult split(List<PageText> pages) {
        Set<String> repeated = repeatedMargins(pages);
        Parser parser = new Parser();
        for (PageText page : pages) {
            String[] lines = page.text().split("\\R");
            List<Integer> nonBlank = new ArrayList<>();
            for (int i = 0; i < lines.length; i++) {
                if (!lines[i].isBlank()) {
                    nonBlank.add(i);
                }
            }
            for (int i = 0; i < lines.length; i++) {
                String line = normalize(lines[i]);
                if (isMargin(line, nonBlank.indexOf(i), nonBlank.size(), repeated)) {
                    continue;
                }
                parser.accept(line, page.page());
            }
        }
        return parser.finish();
    }

    // ---- 頁首頁尾 ----

    private static final int MARGIN_LINES = 3;

    /** 在一半以上頁面的頁首／頁尾區重複出現的行（數字視為相同、不計字詞順序）。 */
    private static Set<String> repeatedMargins(List<PageText> pages) {
        if (pages.size() < 3) {
            return Set.of();
        }
        Map<String, Integer> counts = new HashMap<>();
        for (PageText page : pages) {
            List<String> lines = page.text().lines().filter(l -> !l.isBlank()).toList();
            Set<String> keys = new HashSet<>();
            for (int i = 0; i < lines.size(); i++) {
                if (i < MARGIN_LINES || i >= lines.size() - MARGIN_LINES) {
                    String key = marginKey(normalize(lines.get(i)));
                    if (key.length() >= 2) {
                        keys.add(key);
                    }
                }
            }
            keys.forEach(k -> counts.merge(k, 1, Integer::sum));
        }
        int threshold = Math.max(3, (pages.size() + 1) / 2);
        Set<String> repeated = new HashSet<>();
        counts.forEach((k, n) -> {
            if (n >= threshold) {
                repeated.add(k);
            }
        });
        return repeated;
    }

    private static String marginKey(String line) {
        String[] tokens = line.strip().replaceAll("\\d+", "#").split("\\s+");
        java.util.Arrays.sort(tokens);
        return String.join(" ", tokens).strip();
    }

    private static boolean isMargin(String line, int nonBlankIndex, int nonBlankCount, Set<String> repeated) {
        if (nonBlankIndex < 0) {
            return false; // 空白行交給 Parser
        }
        String s = line.strip();
        if (PAGE_NUMBER.matcher(s).matches()) {
            return true;
        }
        boolean inMargin = nonBlankIndex < MARGIN_LINES || nonBlankIndex >= nonBlankCount - MARGIN_LINES;
        return inMargin && repeated.contains(marginKey(line));
    }

    // ---- 逐行處理 ----

    private static final class Parser {
        final List<Block> blocks = new ArrayList<>();
        final List<GroupBuilder> groups = new ArrayList<>();
        final List<String> loose = new ArrayList<>();
        int loosePage;
        Block current;
        GroupBuilder group;
        Section section;
        Integer lastNumber;
        boolean instructionContinues;

        void accept(String line, int page) {
            String s = line.strip();
            if (s.isEmpty()) {
                instructionContinues = false;
                return;
            }
            String compact = s.replaceAll("\\s+", "");
            if (instructionContinues) {
                instructionContinues = !endsSentence(compact);
                updateSection(compact);
                return;
            }
            if (INSTRUCTION.matcher(compact).find()) {
                closeAll();
                updateSection(compact);
                instructionContinues = !endsSentence(compact);
                return;
            }
            int[] range = groupRange(s, compact);
            if (range != null) {
                closeAll();
                group = new GroupBuilder(range[0], range[1], page, section == Section.UNSUPPORTED);
                return;
            }
            if (ANNOTATION.matcher(compact).matches()) {
                if (current != null) {
                    current.hint = sectionOf(compact);
                }
                return;
            }
            Matcher m = QUESTION_START.matcher(line);
            if (isTitle(compact) && !m.matches()) {
                closeAll();
                updateSection(compact);
                return;
            }
            Integer number = m.matches() ? parseNumber(m) : null;
            if (number != null && acceptsNumber(number)) {
                startQuestion(number, m.group(4), page);
                return;
            }
            append(line, s, page);
        }

        private boolean acceptsNumber(int number) {
            if (number <= 0 || number > MAX_QUESTION_NUMBER || lastNumber != null && number <= lastNumber) {
                return false;
            }
            if (group != null) {
                if (number < group.first) {
                    return false; // 題組文章裡的數字
                }
                if (number > group.last) {
                    closeGroup();
                }
            }
            return true;
        }

        private void startQuestion(int number, String rest, int page) {
            String passage = null;
            int passagePage = page;
            if (group == null) {
                if (current == null && lastNumber != null && chars(loose) > LONG_PASSAGE_CHARS) {
                    passage = String.join("\n", loose);
                    passagePage = loosePage;
                } else if (current != null) {
                    passage = current.takeTrailingPassage();
                    passagePage = current.page;
                }
            }
            current = null;
            loose.clear();
            Block block = new Block(number, page, section);
            block.lines.add(rest);
            if (group != null) {
                block.group = group;
                group.blocks.add(block);
            } else if (passage != null) {
                GroupBuilder guessed = new GroupBuilder(null, null, passagePage, false);
                guessed.lines.addAll(passage.lines().toList());
                guessed.warnings.add("可能是題組文章，請確認範圍");
                guessed.blocks.add(block);
                block.group = guessed;
                finishGroup(guessed);
            }
            blocks.add(block);
            current = block;
            lastNumber = number;
        }

        private void append(String line, String stripped, int page) {
            if (current != null) {
                current.lines.add(stripped);
            } else if (group != null) {
                group.lines.add(line); // 保留空白，供標記空格使用
            } else {
                if (loose.isEmpty()) {
                    loosePage = page;
                }
                loose.add(stripped);
            }
        }

        private void updateSection(String compact) {
            Section s = sectionOf(compact);
            if (s != null) {
                section = s;
            }
        }

        private void closeAll() {
            current = null;
            closeGroup();
            loose.clear();
        }

        private void closeGroup() {
            if (group == null) {
                return;
            }
            current = null;
            finishGroup(group);
            group = null;
        }

        /** 判斷題組種類、產生缺少的題目、標記空格，並排進結果。 */
        private void finishGroup(GroupBuilder g) {
            groups.add(g);
            if (g.first == null) {
                return; // 由長文章推測的題組，只有一題
            }
            List<String> passageLines = new ArrayList<>(g.lines);
            List<String> pool = List.of();
            boolean cloze = !g.blocks.isEmpty() && g.blocks.stream().allMatch(Block::hasEmptyStem);
            if (g.blocks.isEmpty()) {
                for (int i = 1; i < g.lines.size(); i++) {
                    if (POOL_START.matcher(g.lines.get(i).strip()).find()) {
                        pool = parseOptions(String.join("\n", g.lines.subList(i, g.lines.size()).stream()
                                .map(String::strip).toList())).options;
                        passageLines = new ArrayList<>(g.lines.subList(0, i));
                        break;
                    }
                }
                if (pool.size() < 2) {
                    pool = List.of();
                    g.warnings.add("題組內找不到題目，請對照原始頁面補上");
                }
            }
            g.markBlanks = !g.unsupported && (cloze || !pool.isEmpty());
            g.lines.clear();
            g.lines.addAll(passageLines);

            Map<Integer, Block> found = new HashMap<>();
            g.blocks.forEach(b -> found.put(b.number, b));
            blocks.removeAll(g.blocks);
            g.blocks.clear();
            for (int n = g.first; n <= g.last; n++) {
                Block b = found.get(n);
                if (b == null) {
                    b = new Block(n, g.page, section);
                    b.group = g;
                    if (!pool.isEmpty()) {
                        b.fixedOptions = pool;
                        b.lines.add("");
                    } else {
                        b.missing = true;
                    }
                }
                if (cloze || !pool.isEmpty()) {
                    b.blankStem = true;
                }
                g.blocks.add(b);
                blocks.add(b);
            }
            lastNumber = lastNumber == null ? g.last : Math.max(lastNumber, g.last);
        }

        SplitResult finish() {
            closeAll();
            List<DraftGroup> draftGroups = new ArrayList<>();
            Map<GroupBuilder, Integer> index = new HashMap<>();
            for (GroupBuilder g : groups) {
                if (g.blocks.isEmpty()) {
                    continue;
                }
                index.put(g, draftGroups.size());
                draftGroups.add(g.toDraft());
            }
            List<DraftQuestion> questions = new ArrayList<>();
            Integer previous = null;
            for (Block b : blocks) {
                questions.add(toDraft(b, previous, b.group == null ? null : index.get(b.group)));
                previous = b.number;
            }
            return new SplitResult(List.copyOf(draftGroups), List.copyOf(questions));
        }
    }

    private static int[] groupRange(String stripped, String compact) {
        Matcher m = null;
        if (compact.length() <= 60) {
            Matcher zh = GROUP_HEADER_ZH.matcher(compact);
            Matcher read = GROUP_HEADER_READ.matcher(compact);
            Matcher en = GROUP_HEADER_EN.matcher(stripped);
            m = zh.find() ? zh : read.find() ? read : en.find() ? en : null;
        }
        if (m == null) {
            return null;
        }
        int first = Integer.parseInt(m.group(1));
        int last = Integer.parseInt(m.group(2));
        if (first < 1 || last <= first || last > MAX_QUESTION_NUMBER || last - first >= MAX_GROUP_SIZE) {
            return null;
        }
        return new int[] {first, last};
    }

    private static boolean isTitle(String compact) {
        if (compact.length() > 40) {
            return false;
        }
        if (PART_TITLE.matcher(compact).find()) {
            return true;
        }
        if (SECTION_TITLE.matcher(compact).find() && SECTION_TITLE_WORDS.matcher(compact).find()) {
            return true;
        }
        // 只有關鍵字的短行，例如「是非題」「單選題（每題 2 分）」；問句不算
        return compact.length() <= 15 && !compact.endsWith("?") && !compact.endsWith("？")
                && sectionOf(compact) != null;
    }

    /** 從標題或說明推測題型；回傳 null 表示沒有線索。 */
    private static Section sectionOf(String compact) {
        if (compact.matches(".*(混合題|非選擇題|填充題|簡答題|問答題|申論題|計算題|證明題|作文|翻譯|中譯英|英譯中).*")) {
            return Section.UNSUPPORTED;
        }
        if (compact.contains("多選") || compact.contains("複選")) {
            return Section.MULTIPLE;
        }
        if (compact.contains("是非")) {
            return Section.TRUE_FALSE;
        }
        if (compact.contains("單選") || compact.contains("選擇題")) {
            return Section.SINGLE;
        }
        return null;
    }

    private static boolean endsSentence(String s) {
        return !s.isEmpty() && "。.？?！!」』\"”’:：;；)".indexOf(s.charAt(s.length() - 1)) >= 0;
    }

    private static int chars(List<String> lines) {
        int n = 0;
        for (String l : lines) {
            n += (int) l.codePoints().filter(c -> !Character.isWhitespace(c)).count();
        }
        return n;
    }

    // ---- 題目 ----

    private record Marker(int start, int end) {
    }

    private record Options(List<Marker> markers, List<String> options, boolean gap, boolean damaged) {
    }

    private static Options parseOptions(String text) {
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
        List<String> options = new ArrayList<>();
        if (markers.size() >= 2) {
            for (int i = 0; i < markers.size(); i++) {
                int end = i + 1 < markers.size() ? markers.get(i + 1).start() : text.length();
                options.add(text.substring(markers.get(i).end(), end).replaceAll("\\s+", " ").strip());
            }
        }
        return new Options(markers, List.copyOf(options), gap, damaged);
    }

    private static DraftQuestion toDraft(Block block, Integer previousNumber, Integer groupIndex) {
        String text = String.join("\n", block.lines).strip();
        List<String> warnings = new ArrayList<>();
        if (previousNumber != null && block.number != previousNumber + 1) {
            warnings.add("題號不連續（前一題是 " + previousNumber + "）");
        }
        boolean unsupported = block.section == Section.UNSUPPORTED || block.hint == Section.UNSUPPORTED
                || block.group != null && block.group.unsupported;
        if (unsupported) {
            warnings.add(UNSUPPORTED_WARNING);
        }

        String stem;
        List<String> options;
        Section kind = block.hint != null ? block.hint : block.section;
        if (block.fixedOptions != null) {
            stem = "第 " + block.number + " 格";
            options = block.fixedOptions;
            kind = Section.SINGLE;
        } else if (block.missing) {
            stem = block.blankStem ? "第 " + block.number + " 格" : "";
            options = List.of();
            warnings.add("找不到這一題，請對照原始頁面補上");
        } else {
            Options parsed = parseOptions(text);
            options = parsed.options;
            if (options.size() >= 2) {
                stem = text.substring(0, parsed.markers.get(0).start()).strip();
                if (parsed.gap) {
                    warnings.add("選項字母不連續，可能有選項沒有辨識出來");
                }
                if (parsed.damaged) {
                    warnings.add("選項記號辨識不完整（例如缺少括號），請確認選項切分是否正確");
                }
                if (options.stream().anyMatch(String::isEmpty)) {
                    warnings.add("有選項內容是空的");
                }
                long optionLines = text.substring(parsed.markers.get(0).start()).lines().count();
                if (optionLines > options.size() * 2L + 2) {
                    warnings.add("選項可能是圖片或圖表（文字零碎），請對照原始頁面確認");
                }
            } else {
                stem = text;
                if (unsupported) {
                    // 非選擇題不檢查選項
                } else if (parsed.markers.size() == 1) {
                    warnings.add("只找到 1 個選項，請確認選項與題型");
                } else if (kind != Section.TRUE_FALSE) {
                    warnings.add("找不到選項，請確認題型");
                }
            }
            if (block.blankStem && stem.isEmpty()) {
                stem = "第 " + block.number + " 格";
            } else if (stem.codePointCount(0, stem.length()) < 4 && !unsupported) {
                warnings.add("題目內容過短，可能辨識不完整");
            }
        }

        QuestionType type;
        if (unsupported) {
            type = null;
        } else if (options.size() >= 2) {
            type = kind == Section.MULTIPLE ? QuestionType.MULTIPLE_CHOICE : QuestionType.SINGLE_CHOICE;
        } else {
            type = kind == Section.TRUE_FALSE ? QuestionType.TRUE_FALSE : null;
        }
        return new DraftQuestion(block.number, stem, List.copyOf(options), type, block.page, List.copyOf(warnings),
                groupIndex, unsupported);
    }

    private static Integer parseNumber(Matcher m) {
        for (int g = 1; g <= 3; g++) {
            if (m.group(g) != null) {
                return Integer.parseInt(m.group(g));
            }
        }
        return null;
    }

    // ---- 題組文章 ----

    /** 克漏字的空格：前後兩個以上空白、底線或括號包住的題號。 */
    private static Pattern blankPattern(int n) {
        return Pattern.compile("_{2,}\\s*" + n + "\\s*_{2,}|\\(\\s*" + n + "\\s*\\)|\\[\\s*" + n + "\\s*]"
                + "|【\\s*" + n + "\\s*】|(?:^|(?<=\\s\\s))" + n + "(?=\\s\\s|\\s*$)");
    }

    /** 把文章的行接成段落：上一行以句號結束且明顯比最長的行短時才換段。 */
    static String joinPassage(List<String> lines) {
        // 文字層常用連續空白排版；接成段落時縮成一個空白，標點前不留空白
        List<String> stripped = lines.stream()
                .map(l -> l.strip().replaceAll("[ \\t]{2,}", " ").replaceAll(" +(?=[.,;:!?，。；：！？])", ""))
                .filter(l -> !l.isEmpty()).toList();
        int max = stripped.stream().mapToInt(String::length).max().orElse(0);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < stripped.size(); i++) {
            String line = stripped.get(i);
            if (i > 0) {
                String prev = stripped.get(i - 1);
                if (endsSentence(prev) && prev.length() < max * 0.8) {
                    out.append('\n');
                } else if (!(isCjk(prev.charAt(prev.length() - 1)) && isCjk(line.charAt(0)))) {
                    out.append(' ');
                }
            }
            out.append(line);
        }
        return out.toString();
    }

    private static boolean isCjk(char c) {
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        // 【】 是空格標記，兩側照一般文字處理
        return script == Character.UnicodeScript.HAN
                || (c >= 0x3000 && c <= 0x303F || c >= 0xFF00 && c <= 0xFFEF) && c != '【' && c != '】';
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

    private static final class Block {
        final int number;
        final int page;
        final Section section;
        final List<String> lines = new ArrayList<>();
        Section hint;
        GroupBuilder group;
        /** 共用選項題組產生的題目 */
        List<String> fixedOptions;
        /** 題組範圍內找不到的題目 */
        boolean missing;
        /** 克漏字與共用選項題組：題幹空白時填入「第 N 格」 */
        boolean blankStem;

        Block(int number, int page, Section section) {
            this.number = number;
            this.page = page;
            this.section = section;
        }

        boolean hasEmptyStem() {
            String text = String.join("\n", lines).strip();
            Options parsed = parseOptions(text);
            return parsed.options.size() >= 2 && parsed.markers.get(0).start() == 0;
        }

        /** 最後一個選項之後、超過 {@link #LONG_PASSAGE_CHARS} 字的無選項文字，可能是下一題的文章；取出並回傳。 */
        String takeTrailingPassage() {
            int lastOptionLine = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (OPTION_MARKER.matcher(lines.get(i)).find()) {
                    lastOptionLine = i;
                }
            }
            if (lastOptionLine < 0 || parseOptions(String.join("\n", lines)).options.size() < 2) {
                return null;
            }
            List<String> tail = lines.subList(lastOptionLine + 1, lines.size());
            if (chars(tail) <= LONG_PASSAGE_CHARS) {
                return null;
            }
            String passage = String.join("\n", tail);
            tail.clear();
            return passage;
        }
    }

    private static final class GroupBuilder {
        final Integer first;
        final Integer last;
        final int page;
        final boolean unsupported;
        final List<String> lines = new ArrayList<>();
        final List<Block> blocks = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        boolean markBlanks;

        GroupBuilder(Integer first, Integer last, int page, boolean unsupported) {
            this.first = first;
            this.last = last;
            this.page = page;
            this.unsupported = unsupported;
        }

        DraftGroup toDraft() {
            List<String> marked = new ArrayList<>(lines);
            if (markBlanks) {
                List<Integer> notFound = new ArrayList<>();
                for (Block b : blocks) {
                    Pattern p = blankPattern(b.number);
                    boolean hit = false;
                    for (int i = 0; i < marked.size(); i++) {
                        Matcher m = p.matcher(marked.get(i));
                        if (m.find()) {
                            hit = true;
                            marked.set(i, m.replaceAll(" 【" + b.number + "】 "));
                        }
                    }
                    if (!hit) {
                        notFound.add(b.number);
                    }
                }
                if (!notFound.isEmpty()) {
                    warnings.add("文章中找不到第 " + notFound.stream().map(String::valueOf)
                            .reduce((a, b) -> a + "、" + b).orElse("") + " 格的空格，請在文章中標出【題號】");
                }
            }
            String passage = joinPassage(marked);
            if (passage.isEmpty()) {
                warnings.add("題組文章是空的，請對照原始頁面補上");
            }
            return new DraftGroup(passage, first, last, page, List.copyOf(warnings));
        }
    }
}
