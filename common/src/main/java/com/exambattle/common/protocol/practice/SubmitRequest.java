package com.exambattle.common.protocol.practice;

import java.util.List;

/** SUBMIT_PRACTICE：考試模式送出全部作答；練習模式送出空清單（以已作答的為準）。 */
public record SubmitRequest(String practiceId, List<AnswerData> answers) {
}
