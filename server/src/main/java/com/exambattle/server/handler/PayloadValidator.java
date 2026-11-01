package com.exambattle.server.handler;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.ProtocolException;
import com.exambattle.common.protocol.payload.ChatRequest;
import com.exambattle.common.protocol.payload.HelloPayload;
import com.exambattle.common.protocol.payload.RegisterRequest;

/** 檢查客戶端送來的 payload 是否符合 docs/PROTOCOL.md 的欄位規則。 */
public final class PayloadValidator {

    private PayloadValidator() {
    }

    public static void hello(HelloPayload hello, String requestId) throws ProtocolException {
        if (hello.protocolVersion() != null && hello.protocolVersion() != Protocol.VERSION) {
            throw invalid("不支援的協定版本: " + hello.protocolVersion(), requestId);
        }
    }

    /** 回傳去除前後空白的顯示名稱；帳號與密碼不做修改。 */
    static String register(RegisterRequest request, String requestId) throws ProtocolException {
        username(request.username(), requestId);
        String password = request.password();
        if (password == null || password.length() < Protocol.MIN_PASSWORD_CHARS
                || password.length() > Protocol.MAX_PASSWORD_CHARS) {
            throw invalid("密碼長度必須為 " + Protocol.MIN_PASSWORD_CHARS + "–"
                    + Protocol.MAX_PASSWORD_CHARS + " 字元", requestId);
        }
        return displayName(request.displayName(), requestId);
    }

    static void username(String username, String requestId) throws ProtocolException {
        if (username == null || !Protocol.USERNAME_PATTERN.matcher(username).matches()) {
            throw invalid("帳號必須是 3–20 個英文字母、數字或底線", requestId);
        }
    }

    static String displayName(String raw, String requestId) throws ProtocolException {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty() || name.length() > Protocol.MAX_DISPLAY_NAME_CHARS) {
            throw invalid("顯示名稱長度必須為 1–" + Protocol.MAX_DISPLAY_NAME_CHARS + " 字元", requestId);
        }
        if (name.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid("顯示名稱不可包含控制字元", requestId);
        }
        return name;
    }

    static String chatText(ChatRequest chat, String requestId) throws ProtocolException {
        String text = chat.text();
        if (text == null || text.isBlank() || text.length() > Protocol.MAX_CHAT_CHARS) {
            throw invalid("text 長度必須為 1–" + Protocol.MAX_CHAT_CHARS + " 字元且不可全為空白", requestId);
        }
        return text;
    }

    static ProtocolException invalid(String message, String requestId) {
        return new ProtocolException(ErrorCode.INVALID_PAYLOAD, message, requestId);
    }
}
