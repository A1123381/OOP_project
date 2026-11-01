package com.exambattle.common.protocol.payload;

import com.exambattle.common.protocol.ErrorCode;

/** ERROR 的內容。 */
public record ErrorPayload(ErrorCode code, String message) {
}
