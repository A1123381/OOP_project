package com.exambattle.common.protocol;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 訊息與單行 JSON 之間的轉換。ObjectMapper 為執行緒安全，整個程式共用一份。
 */
public final class MessageCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private MessageCodec() {
    }

    /** 將訊息編碼成單行 JSON（不含結尾換行）。字串內的換行會被跳脫成 \n。 */
    public static String encode(Message message) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("type", message.type());
        root.put("requestId", message.requestId());
        root.set("payload", message.payload());
        try {
            return MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("無法編碼訊息", e);
        }
    }

    /**
     * 解析一行 JSON。格式錯誤或缺少必填欄位時丟出 BAD_JSON；
     * 若已讀到 requestId，會放進例外中以便回應。
     */
    public static Message decode(String line) throws ProtocolException {
        JsonNode root;
        try {
            root = MAPPER.readTree(line);
        } catch (JsonProcessingException e) {
            throw new ProtocolException(ErrorCode.BAD_JSON, "不是合法的 JSON");
        }
        if (root == null || !root.isObject()) {
            throw new ProtocolException(ErrorCode.BAD_JSON, "訊息必須是 JSON 物件");
        }

        JsonNode requestIdNode = root.get("requestId");
        String requestId = null;
        if (requestIdNode != null && !requestIdNode.isNull()) {
            if (!requestIdNode.isTextual()) {
                throw new ProtocolException(ErrorCode.BAD_JSON, "requestId 必須是字串或 null");
            }
            requestId = requestIdNode.asText();
        }

        JsonNode typeNode = root.get("type");
        if (typeNode == null || !typeNode.isTextual() || typeNode.asText().isBlank()) {
            throw new ProtocolException(ErrorCode.BAD_JSON, "缺少 type", requestId);
        }
        JsonNode payloadNode = root.get("payload");
        if (payloadNode == null || !payloadNode.isObject()) {
            throw new ProtocolException(ErrorCode.BAD_JSON, "payload 必須是 JSON 物件", requestId);
        }
        return new Message(typeNode.asText(), requestId, (ObjectNode) payloadNode);
    }

    static ObjectNode toPayload(Object payload) {
        if (payload == null) {
            return MAPPER.createObjectNode();
        }
        JsonNode node = MAPPER.valueToTree(payload);
        if (!(node instanceof ObjectNode objectNode)) {
            throw new IllegalArgumentException("payload 必須能轉成 JSON 物件: " + payload.getClass());
        }
        return objectNode;
    }

    static <T> T fromPayload(ObjectNode payload, Class<T> payloadType, String requestId)
            throws ProtocolException {
        try {
            return MAPPER.treeToValue(payload, payloadType);
        } catch (JsonProcessingException | IllegalArgumentException e) {
            throw new ProtocolException(ErrorCode.INVALID_PAYLOAD, "payload 欄位格式錯誤", requestId);
        }
    }
}
