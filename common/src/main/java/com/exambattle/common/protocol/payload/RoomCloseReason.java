package com.exambattle.common.protocol.payload;

/** 房間解散的原因：房主離開即解散（見 DECISIONS.md D-012）。 */
public enum RoomCloseReason {
    OWNER_LEFT,
    OWNER_DISCONNECTED
}
