package com.exambattle.common.protocol.game;

import java.util.List;

/** SUBMIT_ANSWER：目前這一題的答案，每題只能送一次。 */
public record SubmitAnswerRequest(String gameId, Integer index, List<Long> selectedOptionIds, Boolean trueFalseAnswer) {
}
