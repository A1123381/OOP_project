package com.exambattle.client.gui.model;

import com.exambattle.common.protocol.bank.OptionData;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestionDraftTest {

    private static QuestionDraft choice(QuestionType type, String... options) {
        List<QuestionDraft.Option> list = java.util.Arrays.stream(options)
                .map(o -> new QuestionDraft.Option(o.replace("*", ""), o.endsWith("*")))
                .toList();
        return new QuestionDraft(type, "題目", "", list, null);
    }

    @Test
    void validSingleChoiceConvertsToData() {
        QuestionData data = choice(QuestionType.SINGLE_CHOICE, " A ", "B*").toData();

        assertEquals(List.of("A", "B"), data.options().stream().map(OptionData::content).toList());
        assertEquals(List.of(false, true), data.options().stream().map(OptionData::correct).toList());
        assertNull(data.explanation(), "空白解說送出為 null");
        assertNull(data.trueFalseAnswer());
    }

    @Test
    void rulesMatchServer() {
        assertTrue(choice(QuestionType.SINGLE_CHOICE, "A*", "B*").validate().isPresent(), "單選題只能有一個正確答案");
        assertTrue(choice(QuestionType.MULTIPLE_CHOICE, "A*", "B*").validate().isEmpty());
        assertTrue(choice(QuestionType.SINGLE_CHOICE, "A*").validate().isPresent(), "至少兩個選項");
        assertTrue(choice(QuestionType.SINGLE_CHOICE, "A*", " ").validate().isPresent(), "選項不可空白");
        assertTrue(new QuestionDraft(QuestionType.TRUE_FALSE, " ", "", List.of(), true).validate().isPresent());
    }

    @Test
    void unsetAnswerIsAllowedButReported() {
        QuestionDraft noAnswer = choice(QuestionType.SINGLE_CHOICE, "A", "B");
        QuestionDraft tfUnset = new QuestionDraft(QuestionType.TRUE_FALSE, "是非", "", List.of(), null);

        assertTrue(noAnswer.validate().isEmpty());
        assertFalse(noAnswer.answerSet());
        assertFalse(tfUnset.answerSet());
        assertNull(tfUnset.toData().trueFalseAnswer());
    }

    @Test
    void trueFalseDropsOptions() {
        QuestionDraft draft = new QuestionDraft(QuestionType.TRUE_FALSE, "是非",
                "解說", List.of(new QuestionDraft.Option("殘留", true)), false);

        QuestionData data = draft.toData();
        assertTrue(data.options().isEmpty());
        assertEquals(Boolean.FALSE, data.trueFalseAnswer());
    }

    @Test
    void roundTripsFromServerData() {
        QuestionData server = new QuestionData(5L, QuestionType.MULTIPLE_CHOICE, "內容", null,
                List.of(new OptionData(1L, "X", true), new OptionData(2L, "Y", false)), null, true);

        QuestionDraft draft = QuestionDraft.from(server);
        assertEquals("", draft.explanation());
        assertEquals(List.of(true, false), draft.options().stream().map(QuestionDraft.Option::correct).toList());
    }

    @Test
    void examDraftValidation() {
        var math = new com.exambattle.common.protocol.bank.SubjectInfo(1, "數學");

        assertTrue(new ExamDraft("期中考", math, "2024", "段考").validate().isEmpty());
        assertTrue(new ExamDraft(" ", math, "", "").validate().isPresent());
        assertTrue(new ExamDraft("期中考", null, "", "").validate().isPresent());
        assertTrue(new ExamDraft("期中考", math, "一一三", "").validate().isPresent());
        assertTrue(new ExamDraft("期中考", math, "1800", "").validate().isPresent());

        var request = new ExamDraft(" 期中考 ", math, " ", " ").toRequest(null);
        assertEquals("期中考", request.title());
        assertNull(request.year());
        assertNull(request.examType());
    }
}
