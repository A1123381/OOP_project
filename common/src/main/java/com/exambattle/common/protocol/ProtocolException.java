package com.exambattle.common.protocol;

/** 收到不符協定的訊息時丟出，攜帶要回給對方的錯誤碼。 */
public class ProtocolException extends Exception {

    private final ErrorCode code;
    private final String requestId;

    public ProtocolException(ErrorCode code, String message) {
        this(code, message, null);
    }

    public ProtocolException(ErrorCode code, String message, String requestId) {
        super(message);
        this.code = code;
        this.requestId = requestId;
    }

    public ErrorCode code() {
        return code;
    }

    /** 造成錯誤的請求 id；無法辨識時為 null。 */
    public String requestId() {
        return requestId;
    }
}
