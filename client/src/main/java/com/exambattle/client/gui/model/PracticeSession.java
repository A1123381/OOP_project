package com.exambattle.client.gui.model;

import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.practice.AnswerData;
import com.exambattle.common.protocol.practice.AnswerResult;
import com.exambattle.common.protocol.practice.PracticeQuestion;
import com.exambattle.common.protocol.practice.PracticeStarted;
import com.exambattle.common.protocol.practice.RevealMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 作答畫面的狀態：目前題號、每題的作答、練習模式已收到的批改結果。
 * 不依賴 JavaFX，可單元測試。對錯與分數一律以伺服器回傳為準。
 */
public final class PracticeSession {

    private final PracticeStarted started;
    private final Map<Integer, Set<Long>> choices = new HashMap<>();
    private final Map<Integer, Boolean> trueFalse = new HashMap<>();
    private final Map<Integer, AnswerResult> results = new HashMap<>();
    private int current;

    public PracticeSession(PracticeStarted started) {
        this.started = started;
    }

    public PracticeStarted started() {
        return started;
    }

    public boolean immediate() {
        return started.revealMode() == RevealMode.IMMEDIATE;
    }

    public int size() {
        return started.questions().size();
    }

    public int current() {
        return current;
    }

    public PracticeQuestion question() {
        return started.questions().get(current);
    }

    public void goTo(int index) {
        if (index >= 0 && index < size()) {
            current = index;
        }
    }

    public boolean hasNext() {
        return current < size() - 1;
    }

    public boolean hasPrevious() {
        return current > 0;
    }

    /** 選擇題作答；單選題只保留一個選項。已批改的題目不能修改。 */
    public void setChoices(int index, Set<Long> optionIds) {
        if (locked(index)) {
            return;
        }
        Set<Long> ids = new LinkedHashSet<>(optionIds);
        if (started.questions().get(index).type() == QuestionType.SINGLE_CHOICE && ids.size() > 1) {
            throw new IllegalArgumentException("單選題只能選一個選項");
        }
        if (ids.isEmpty()) {
            choices.remove(index);
        } else {
            choices.put(index, ids);
        }
    }

    public void setTrueFalse(int index, Boolean answer) {
        if (locked(index)) {
            return;
        }
        if (answer == null) {
            trueFalse.remove(index);
        } else {
            trueFalse.put(index, answer);
        }
    }

    public Set<Long> choices(int index) {
        return choices.getOrDefault(index, Set.of());
    }

    public Boolean trueFalse(int index) {
        return trueFalse.get(index);
    }

    public boolean isAnswered(int index) {
        return choices.containsKey(index) || trueFalse.containsKey(index);
    }

    public int unansweredCount() {
        int count = 0;
        for (int i = 0; i < size(); i++) {
            if (!isAnswered(i)) {
                count++;
            }
        }
        return count;
    }

    /** 某題的作答；未作答時為 null。 */
    public AnswerData answer(int index) {
        if (!isAnswered(index)) {
            return null;
        }
        return new AnswerData(index, new ArrayList<>(choices(index)), trueFalse(index));
    }

    /** 考試模式交卷時送出的作答（只含已作答的題目）。 */
    public List<AnswerData> answersToSubmit() {
        List<AnswerData> answers = new ArrayList<>();
        for (int i = 0; i < size(); i++) {
            AnswerData a = answer(i);
            if (a != null) {
                answers.add(a);
            }
        }
        return answers;
    }

    /** 練習模式：記錄伺服器的批改結果，該題之後不能修改。 */
    public void recordResult(AnswerResult result) {
        results.put(result.index(), result);
    }

    public AnswerResult result(int index) {
        return results.get(index);
    }

    public boolean locked(int index) {
        return results.containsKey(index);
    }
}
