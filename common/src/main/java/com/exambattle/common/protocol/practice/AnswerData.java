package com.exambattle.common.protocol.practice;

import java.util.List;

/**
 * 一題的作答。選擇題用 selectedOptionIds，是非題用 trueFalseAnswer；兩者皆空表示未作答。
 */
public record AnswerData(Integer index, List<Long> selectedOptionIds, Boolean trueFalseAnswer) {
}
