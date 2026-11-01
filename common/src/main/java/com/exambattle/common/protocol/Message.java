package com.exambattle.common.protocol;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Objects;

/**
 * 線路上的一則訊息：{@code {type, requestId, payload}}。
 *
 * @param type      訊息類型字串（保留原字串，以便回報 UNKNOWN_TYPE）
 * @param requestId 請求識別碼；伺服器主動推播時為 null
 * @param payload   訊息內容，一定是 JSON 物件
 */
public record Message(String type, String requestId, ObjectNode payload) {

    public Message {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(payload, "payload");
    }

    /** 以 payload 物件（record 或 Map）建立訊息。 */
    public static Message of(MessageType type, String requestId, Object payload) {
        return new Message(type.name(), requestId, MessageCodec.toPayload(payload));
    }

    /** 建立沒有內容的訊息（payload 為 {}）。 */
    public static Message empty(MessageType type, String requestId) {
        return of(type, requestId, null);
    }

    /** 將 payload 轉成指定型別；欄位不符時丟出 INVALID_PAYLOAD。 */
    public <T> T payloadAs(Class<T> payloadType) throws ProtocolException {
        return MessageCodec.fromPayload(payload, payloadType, requestId);
    }

    public boolean is(MessageType messageType) {
        return messageType.name().equals(type);
    }
}
