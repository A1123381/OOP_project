package com.exambattle.common.protocol.game;

import com.exambattle.common.protocol.payload.UserInfo;

import java.util.List;

/** GAME_STARTED：競賽開始，firstQuestionInMs 毫秒後送出第一題。 */
public record GameStarted(String gameId, String roomCode, String title, int questionCount, int secondsPerQuestion,
                          List<UserInfo> players, long firstQuestionInMs) {
}
