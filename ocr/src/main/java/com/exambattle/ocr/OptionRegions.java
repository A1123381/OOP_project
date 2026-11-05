package com.exambattle.ocr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 圖片選項的自動截圖範圍（見 DECISIONS.md D-045）。只看座標，不用 AI：
 * <ol>
 *   <li>在題號之後、下一題題號之前，依字母順序找出 (A)(B)(C)… 選項記號的位置</li>
 *   <li>把記號依高度分成列（一列四個、兩欄兩列、一列一個都可以）</li>
 *   <li>每個選項的範圍：從它的記號開始，到同一列下一個記號（或內容右邊界）為止，
 *       往下到下一列的記號（或下一題題號、頁面內容底部）為止；截圖包含選項記號本身</li>
 * </ol>
 * 範圍太矮（只夠放一行字）時視為文字選項，不截圖。結果只是草稿，使用者可以重框。
 */
public final class OptionRegions {

    /** 沒有下一題時，截到頁面這個高度為止（避開頁尾）。 */
    static final double PAGE_BOTTOM = 0.94;
    /** 內容的右邊界（沒有更右邊的字時）。 */
    static final double PAGE_RIGHT = 0.95;
    private static final double PAD = 0.004;

    private OptionRegions() {
    }

    /**
     * @param words       這一頁的字詞位置
     * @param page        頁碼（寫進結果）
     * @param number      題號；null 表示從頁首開始找
     * @param nextNumber  下一題題號；null 表示截到頁面內容底部
     * @param optionCount 選項數
     * @return 每個選項一塊；找不到全部記號、或判斷為文字選項時為 empty
     */
    public static Optional<List<Region>> locate(List<TextBox> words, int page, Integer number, Integer nextNumber,
                                                int optionCount) {
        if (optionCount < 2) {
            return Optional.empty();
        }
        double start = 0;
        if (number != null) {
            Optional<TextBox> q = first(words, numberPattern(number), 0);
            if (q.isEmpty()) {
                return Optional.empty();
            }
            start = q.get().y();
        }
        double bottomLimit = PAGE_BOTTOM;
        if (nextNumber != null) {
            Optional<TextBox> next = first(words, numberPattern(nextNumber), start + PAD);
            if (next.isPresent()) {
                bottomLimit = next.get().y();
            }
        }
        final double end = bottomLimit;

        List<TextBox> markers = new ArrayList<>();
        double from = start;
        for (int i = 0; i < optionCount; i++) {
            Pattern p = markerPattern((char) ('A' + i));
            double floor = from;
            Optional<TextBox> m = words.stream()
                    .filter(w -> w.y() >= floor - PAD && w.y() < end && p.matcher(QuestionSplitter.normalize(w.text())).matches())
                    .min(Comparator.comparingDouble(TextBox::y).thenComparingDouble(TextBox::x));
            if (m.isEmpty()) {
                return Optional.empty();
            }
            markers.add(m.get());
            from = m.get().y();
        }

        // 依高度分列：與該列第一個記號的頂端相差不到記號高度的 0.8 倍
        List<List<TextBox>> rows = new ArrayList<>();
        for (TextBox m : markers) {
            List<TextBox> row = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            if (row != null && Math.abs(row.get(0).y() - m.y()) < Math.max(row.get(0).height(), m.height()) * 0.8) {
                row.add(m);
            } else {
                rows.add(new ArrayList<>(List.of(m)));
            }
        }

        double right = Math.min(PAGE_RIGHT, words.stream().mapToDouble(TextBox::right).max().orElse(PAGE_RIGHT) + PAD);
        List<Region> regions = new ArrayList<>();
        for (int r = 0; r < rows.size(); r++) {
            List<TextBox> row = rows.get(r);
            row.sort(Comparator.comparingDouble(TextBox::x));
            double top = row.stream().mapToDouble(TextBox::y).min().orElseThrow() - PAD;
            double bottom = r + 1 < rows.size()
                    ? rows.get(r + 1).stream().mapToDouble(TextBox::y).min().orElseThrow() - PAD
                    : end - PAD;
            double markerHeight = row.stream().mapToDouble(TextBox::height).max().orElseThrow();
            if (bottom - top < markerHeight * 2.5) {
                return Optional.empty(); // 只夠放一行字：文字選項
            }
            for (int c = 0; c < row.size(); c++) {
                double left = row.get(c).x() - PAD;
                double rightEdge = c + 1 < row.size() ? row.get(c + 1).x() - PAD : right;
                regions.add(new Region(page, Math.max(0, left), Math.max(0, top), rightEdge - left, bottom - top));
            }
        }
        // 依字母順序（列內已依 x 排序；字母順序在多欄版面通常也是由左到右、由上到下）
        regions.sort(Comparator.comparingDouble(Region::y).thenComparingDouble(Region::x));
        return Optional.of(List.copyOf(regions));
    }

    private static Optional<TextBox> first(List<TextBox> words, Pattern p, double minY) {
        return words.stream()
                .filter(w -> w.y() >= minY && p.matcher(QuestionSplitter.normalize(w.text())).matches())
                .min(Comparator.comparingDouble(TextBox::y).thenComparingDouble(TextBox::x));
    }

    private static Pattern numberPattern(int number) {
        return Pattern.compile("^(?:\\(\\s*" + number + "\\s*\\)|" + number + "[.、)])$");
    }

    private static Pattern markerPattern(char letter) {
        return Pattern.compile("^(?:\\(\\s*" + letter + "\\s*\\)|" + letter + "[.、)]).*$");
    }
}
