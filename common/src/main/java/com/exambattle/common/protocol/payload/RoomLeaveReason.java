package com.exambattle.common.protocol.payload;

/** 玩家離開房間的原因。 */
public enum RoomLeaveReason {
    LEFT,
    DISCONNECTED,
    /** 被房主移出（第四階段）。 */
    KICKED
}
