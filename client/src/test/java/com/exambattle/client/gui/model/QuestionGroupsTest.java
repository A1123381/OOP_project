package com.exambattle.client.gui.model;

import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestionGroupsTest {

    private static QuestionData q(Long group, String passage) {
        return new QuestionData(1L, QuestionType.TRUE_FALSE, "題", null, List.of(), null, false, group, passage);
    }

    @Test
    void labelsUsePositionsInExam() {
        List<QuestionGroups.Group> groups = QuestionGroups.of(List.of(
                q(null, null), q(7L, "甲文"), q(7L, "甲文"), q(7L, "甲文"), q(9L, "乙文")));

        assertEquals(List.of(new QuestionGroups.Group(7, "題組 2–4", "甲文"), new QuestionGroups.Group(9, "題組 5", "乙文")),
                groups);
        assertEquals("題組 2–4", QuestionGroups.labelOf(groups, 7L));
        assertEquals("題組", QuestionGroups.labelOf(groups, 1L));
    }
}
