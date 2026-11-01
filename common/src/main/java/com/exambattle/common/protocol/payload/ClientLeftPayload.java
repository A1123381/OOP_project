package com.exambattle.common.protocol.payload;

/** CLIENT_LEFT：某位已登入的使用者離線。 */
public record ClientLeftPayload(UserInfo user, LeaveReason reason) {
}
