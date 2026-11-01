package com.exambattle.common.protocol.payload;

/** BYE：reason 選填；伺服器關閉時為 SERVER_SHUTDOWN。 */
public record ByePayload(String reason) {

    public static final String SERVER_SHUTDOWN = "SERVER_SHUTDOWN";
}
