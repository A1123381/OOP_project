package com.exambattle.client.net;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.ProtocolException;
import com.exambattle.common.protocol.payload.ErrorPayload;

/** 伺服器以 ERROR 回應了 {@link ServerConnection#request} 送出的請求。 */
public class ServerErrorException extends RuntimeException {

    private final ErrorCode code;

    public ServerErrorException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    static ServerErrorException from(Message error) {
        try {
            ErrorPayload payload = error.payloadAs(ErrorPayload.class);
            return new ServerErrorException(payload.code(), payload.message());
        } catch (ProtocolException e) {
            return new ServerErrorException(ErrorCode.INTERNAL, "無法解析伺服器的錯誤訊息");
        }
    }
}
