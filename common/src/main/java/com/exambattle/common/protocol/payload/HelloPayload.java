package com.exambattle.common.protocol.payload;

/** HELLO：連線握手。protocolVersion 選填；身分改由 LOGIN 取得。 */
public record HelloPayload(Integer protocolVersion) {
}
