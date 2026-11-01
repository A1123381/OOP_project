package com.exambattle.common.protocol.payload;

/** 一位已登入使用者的公開資訊；也是 CLIENT_JOINED、REGISTER_OK 的 payload。 */
public record UserInfo(long userId, String username, String displayName) {
}
