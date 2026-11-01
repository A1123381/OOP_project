package com.exambattle.common.protocol.payload;

/**
 * 伺服器廣播的 CHAT；serverTime 為 ISO-8601 UTC，由伺服器填入。
 * roomCode 為 null 表示大廳聊天。
 */
public record ChatBroadcast(long fromUserId, String fromName, String text, String serverTime, String roomCode) {
}
