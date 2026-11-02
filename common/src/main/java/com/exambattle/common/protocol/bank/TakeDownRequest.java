package com.exambattle.common.protocol.bank;

/** TAKE_DOWN_EXAM（管理員）：takenDown 為 true 時下架（改為私人），false 時解除下架（仍為私人）。 */
public record TakeDownRequest(Long examId, boolean takenDown) {
}
