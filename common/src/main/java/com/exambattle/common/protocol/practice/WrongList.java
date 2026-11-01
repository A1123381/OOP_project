package com.exambattle.common.protocol.practice;

import java.util.List;

/** WRONG_LIST：依最近答錯時間由新到舊。 */
public record WrongList(List<WrongItem> items) {
}
