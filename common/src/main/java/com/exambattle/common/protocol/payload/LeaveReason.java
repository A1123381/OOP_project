package com.exambattle.common.protocol.payload;

/** CLIENT_LEFT 的原因。KICKED 保留給日後的房主權限（尚待確認）。 */
public enum LeaveReason {
    BYE,
    DISCONNECTED,
    TIMEOUT,
    KICKED
}
