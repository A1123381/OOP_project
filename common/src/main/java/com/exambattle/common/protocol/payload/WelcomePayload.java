package com.exambattle.common.protocol.payload;

/** WELCOME：伺服器接受連線，指派本次連線的 clientId。 */
public record WelcomePayload(String clientId, int protocolVersion) {
}
