package com.exambattle.client.gui.model;

import com.exambattle.common.protocol.game.PlayerResult;
import com.exambattle.common.protocol.practice.PracticeQuestion;

import java.util.ArrayList;
import java.util.List;

/** 競賽畫面的文字（不依賴 JavaFX，可單元測試）。分數一律來自伺服器，這裡只負責顯示。 */
public final class GameText {

    private GameText() {
    }

    /** 自己在這一題的結果，例如「✔ 答對！+1100（連續答對加成 +100）」。 */
    public static String verdict(PlayerResult r) {
        if (!r.answered()) {
            return "⏱ 沒有作答　+0";
        }
        if (r.correct()) {
            return "✔ 答對！+" + r.points() + (r.bonus() > 0 ? "（連續答對 " + r.streak() + " 題，加成 +" + r.bonus() + "）" : "");
        }
        return r.points() > 0 ? "△ 部分給分　+" + r.points() : "✘ 答錯　+0";
    }

    /** 正確答案的文字：選項字母（依題目中的順序）或對／錯。 */
    public static String correctAnswer(PracticeQuestion q, List<Long> correctOptionIds, Boolean correctTrueFalse) {
        if (q.options().isEmpty()) {
            return correctTrueFalse == null ? "未設定" : correctTrueFalse ? "對" : "錯";
        }
        List<String> letters = new ArrayList<>();
        for (int i = 0; i < q.options().size(); i++) {
            if (correctOptionIds.contains(q.options().get(i).id())) {
                letters.add(ResultText.letter(i));
            }
        }
        return String.join("、", letters);
    }

    /** 倒數的整數秒（無條件進位，最少 0）。 */
    public static long secondsLeft(long remainingMillis) {
        return remainingMillis <= 0 ? 0 : (remainingMillis + 999) / 1000;
    }
}
