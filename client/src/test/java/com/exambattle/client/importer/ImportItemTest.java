package com.exambattle.client.importer;

import com.exambattle.client.gui.model.QuestionDraft;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.ocr.AnswerKey;
import com.exambattle.ocr.DraftQuestion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 校正項目的狀態判斷與答案卷套用。 */
class ImportItemTest {

    private static ImportItem item(int number, QuestionType type, String... options) {
        return ImportItem.from(new DraftQuestion(number, "第 " + number + " 題的內容", List.of(options), type, 1, List.of()));
    }

    @Test
    void draftsStartWithoutAnswersAndStatusFollowsValidation() {
        ImportItem single = item(1, QuestionType.SINGLE_CHOICE, "甲", "乙");
        ImportItem unknown = item(2, null);
        ImportItem oneOption = item(3, QuestionType.SINGLE_CHOICE, "甲");

        assertEquals(ImportItem.Status.NO_ANSWER, single.status(), "不推測答案");
        assertEquals(ImportItem.Status.NEEDS_FIX, unknown.status(), "題型未選");
        assertEquals("請選擇題型", unknown.problem().orElseThrow());
        assertEquals(ImportItem.Status.NEEDS_FIX, oneOption.status());
        assertTrue(single.selected());
    }

    @Test
    void trueFalseGuessWithOptionsIsLeftForUser() {
        ImportItem odd = item(1, QuestionType.TRUE_FALSE, "甲", "乙");
        assertNull(odd.draft().type());
    }

    @Test
    void importedItemsAreLockedOut() {
        ImportItem done = item(1, QuestionType.SINGLE_CHOICE, "甲", "乙");
        done.markImported();

        assertEquals(ImportItem.Status.IMPORTED, done.status());
        assertFalse(done.selected());
        AnswerKeyApplier.Summary s = AnswerKeyApplier.apply(List.of(done), Map.of(1, new AnswerKey("A")));
        assertEquals(List.of(1), s.notFound(), "已匯入的題目不會被修改");
    }

    @Test
    void dataRoundTrip() {
        ImportItem original = item(7, QuestionType.MULTIPLE_CHOICE, "甲", "乙", "丙");
        original.setInclude(false);

        ImportItem copy = ImportItem.fromData(original.toData());

        assertEquals(original.toData(), copy.toData());
        assertFalse(copy.include());
    }

    @Test
    void applyAnswerKeyToChoiceAndTrueFalse() {
        ImportItem single = item(1, QuestionType.SINGLE_CHOICE, "甲", "乙", "丙");
        ImportItem multi = item(2, QuestionType.MULTIPLE_CHOICE, "甲", "乙", "丙");
        ImportItem tf = item(3, QuestionType.TRUE_FALSE);

        AnswerKeyApplier.Summary s = AnswerKeyApplier.apply(List.of(single, multi, tf),
                Map.of(1, new AnswerKey("B"), 2, new AnswerKey("AC"), 3, new AnswerKey("F"), 9, new AnswerKey("A")));

        assertEquals(3, s.applied());
        assertEquals(List.of(9), s.notFound());
        assertEquals(List.of(false, true, false), single.draft().options().stream().map(QuestionDraft.Option::correct).toList());
        assertEquals(List.of(true, false, true), multi.draft().options().stream().map(QuestionDraft.Option::correct).toList());
        assertEquals(Boolean.FALSE, tf.draft().trueFalseAnswer(), "是非題的 F 解讀為「錯」");
        assertEquals(ImportItem.Status.READY, single.status());
    }

    @Test
    void mismatchedAnswersAreReportedNotGuessed() {
        ImportItem single = item(1, QuestionType.SINGLE_CHOICE, "甲", "乙");
        ImportItem untyped = item(2, null, "甲", "乙");
        ImportItem shortOptions = item(3, QuestionType.MULTIPLE_CHOICE, "甲", "乙");
        ImportItem tf = item(4, QuestionType.TRUE_FALSE);
        ImportItem dupA = item(5, QuestionType.SINGLE_CHOICE, "甲", "乙");
        ImportItem dupB = item(5, QuestionType.SINGLE_CHOICE, "丙", "丁");

        AnswerKeyApplier.Summary s = AnswerKeyApplier.apply(List.of(single, untyped, shortOptions, tf, dupA, dupB),
                Map.of(1, new AnswerKey("AB"), 2, new AnswerKey("A"), 3, new AnswerKey("D"),
                        4, new AnswerKey("B"), 5, new AnswerKey("A")));

        assertEquals(0, s.applied());
        assertEquals(5, s.problems().size(), s.problems().toString());
        assertTrue(s.problems().stream().anyMatch(p -> p.contains("單選題卻有多個答案")));
        assertTrue(s.problems().stream().anyMatch(p -> p.contains("尚未選擇題型")));
        assertTrue(s.problems().stream().anyMatch(p -> p.contains("超出選項數")));
        assertTrue(s.problems().stream().anyMatch(p -> p.contains("不是對／錯記號")));
        assertTrue(s.problems().stream().anyMatch(p -> p.contains("相同題號")));
        assertFalse(single.draft().answerSet());
    }
}
