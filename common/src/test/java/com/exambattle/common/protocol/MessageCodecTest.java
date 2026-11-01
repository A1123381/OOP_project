package com.exambattle.common.protocol;

import com.exambattle.common.protocol.payload.ChatRequest;
import com.exambattle.common.protocol.payload.ClientLeftPayload;
import com.exambattle.common.protocol.payload.HelloPayload;
import com.exambattle.common.protocol.payload.LeaveReason;
import com.exambattle.common.protocol.payload.LoginRequest;
import com.exambattle.common.protocol.payload.UserInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageCodecTest {

    @Test
    void roundTripKeepsAllFields() throws Exception {
        Message original = Message.of(MessageType.HELLO, "c-1", new HelloPayload(1));

        Message decoded = MessageCodec.decode(MessageCodec.encode(original));

        assertEquals("HELLO", decoded.type());
        assertEquals("c-1", decoded.requestId());
        assertEquals(new HelloPayload(1), decoded.payloadAs(HelloPayload.class));
    }

    @Test
    void encodedMessageIsSingleLineEvenWithNewlinesInText() throws Exception {
        Message original = Message.of(MessageType.CHAT, "c-2", new ChatRequest("第一行\n第二行"));

        String line = MessageCodec.encode(original);

        assertFalse(line.contains("\n"));
        assertEquals("第一行\n第二行", MessageCodec.decode(line).payloadAs(ChatRequest.class).text());
    }

    @Test
    void pushMessageEncodesNullRequestIdAndEmptyPayload() {
        String line = MessageCodec.encode(Message.empty(MessageType.PING, null));

        assertEquals("{\"type\":\"PING\",\"requestId\":null,\"payload\":{}}", line);
    }

    @Test
    void enumsAreEncodedByName() throws Exception {
        Message m = Message.of(MessageType.CLIENT_LEFT, null,
                new ClientLeftPayload(new UserInfo(7, "bob", "Bob"), LeaveReason.TIMEOUT));

        assertTrue(MessageCodec.encode(m).contains("\"reason\":\"TIMEOUT\""));
    }

    @Test
    void missingRequestIdIsTreatedAsNull() throws Exception {
        Message m = MessageCodec.decode("{\"type\":\"PING\",\"payload\":{}}");

        assertNull(m.requestId());
    }

    @Test
    void unknownPayloadFieldsAreIgnored() throws Exception {
        Message m = MessageCodec.decode(
                "{\"type\":\"CHAT\",\"requestId\":\"c-3\",\"payload\":{\"text\":\"hi\",\"extra\":42}}");

        assertEquals("hi", m.payloadAs(ChatRequest.class).text());
    }

    @Test
    void invalidJsonIsBadJson() {
        ProtocolException e = assertThrows(ProtocolException.class, () -> MessageCodec.decode("{not json"));
        assertEquals(ErrorCode.BAD_JSON, e.code());
    }

    @Test
    void nonObjectIsBadJson() {
        ProtocolException e = assertThrows(ProtocolException.class, () -> MessageCodec.decode("[1,2]"));
        assertEquals(ErrorCode.BAD_JSON, e.code());
    }

    @Test
    void missingTypeIsBadJsonAndKeepsRequestId() {
        ProtocolException e = assertThrows(ProtocolException.class,
                () -> MessageCodec.decode("{\"requestId\":\"c-9\",\"payload\":{}}"));
        assertEquals(ErrorCode.BAD_JSON, e.code());
        assertEquals("c-9", e.requestId());
    }

    @Test
    void missingPayloadIsBadJson() {
        ProtocolException e = assertThrows(ProtocolException.class,
                () -> MessageCodec.decode("{\"type\":\"PING\",\"requestId\":\"c-1\"}"));
        assertEquals(ErrorCode.BAD_JSON, e.code());
    }

    @Test
    void nonStringRequestIdIsBadJson() {
        ProtocolException e = assertThrows(ProtocolException.class,
                () -> MessageCodec.decode("{\"type\":\"PING\",\"requestId\":5,\"payload\":{}}"));
        assertEquals(ErrorCode.BAD_JSON, e.code());
    }

    @Test
    void wrongPayloadFieldTypeIsInvalidPayload() throws Exception {
        Message m = MessageCodec.decode(
                "{\"type\":\"HELLO\",\"requestId\":\"c-1\",\"payload\":{\"protocolVersion\":\"abc\"}}");

        ProtocolException e = assertThrows(ProtocolException.class, () -> m.payloadAs(HelloPayload.class));
        assertEquals(ErrorCode.INVALID_PAYLOAD, e.code());
        assertEquals("c-1", e.requestId());
    }

    @Test
    void credentialsAreNotPrintedByToString() {
        assertFalse(new LoginRequest("alice", "s3cret-pass").toString().contains("s3cret-pass"));
    }

    @Test
    void messageTypeFromWire() {
        assertEquals(MessageType.CHAT, MessageType.fromWire("CHAT").orElseThrow());
        assertTrue(MessageType.fromWire("chat").isEmpty());
        assertTrue(MessageType.fromWire(null).isEmpty());
        assertFalse(MessageType.WELCOME.isClientSendable());
        assertTrue(MessageType.BYE.isClientSendable());
        assertTrue(MessageType.JOIN_ROOM.isClientSendable());
        assertFalse(MessageType.ROOM_STATE.isClientSendable());
    }
}
