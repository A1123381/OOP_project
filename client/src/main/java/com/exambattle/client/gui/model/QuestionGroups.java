package com.exambattle.client.gui.model;

import com.exambattle.common.protocol.bank.QuestionData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 考卷中題組的標示（例如「題組 3–7」，依題目在考卷中的位置），見 DECISIONS.md D-034。 */
public final class QuestionGroups {

    /** 一個題組：id、標示與文章。 */
    public record Group(long id, String label, String passage) {
    }

    private QuestionGroups() {
    }

    /** 依題目順序列出考卷中的題組；標示以第一題與最後一題的位置（從 1 開始）表示。 */
    public static List<Group> of(List<QuestionData> questions) {
        Map<Long, int[]> ranges = new LinkedHashMap<>();
        Map<Long, String> passages = new LinkedHashMap<>();
        for (int i = 0; i < questions.size(); i++) {
            QuestionData q = questions.get(i);
            if (q.groupId() == null) {
                continue;
            }
            int position = i + 1;
            ranges.computeIfAbsent(q.groupId(), k -> new int[] {position, position})[1] = position;
            passages.putIfAbsent(q.groupId(), q.passage() == null ? "" : q.passage());
        }
        return ranges.entrySet().stream()
                .map(e -> new Group(e.getKey(), label(e.getValue()[0], e.getValue()[1]), passages.get(e.getKey())))
                .toList();
    }

    /** groupId 對應的標示；找不到時為「題組」。 */
    public static String labelOf(List<Group> groups, Long groupId) {
        return groups.stream().filter(g -> groupId != null && g.id() == groupId).map(Group::label)
                .findFirst().orElse("題組");
    }

    private static String label(int first, int last) {
        return first == last ? "題組 " + first : "題組 " + first + "–" + last;
    }
}
