package com.exambattle.common.protocol.game;

/** ANSWER_ACCEPTED：伺服器已收到答案；對錯在這一題結束時才公布。 */
public record AnswerAccepted(String gameId, int index) {
}
