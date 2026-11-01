package com.exambattle.common.io;

import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageCodec;

import java.io.IOException;
import java.io.Writer;

/** 將訊息寫成「一行一個 JSON」並立即 flush。非執行緒安全，由呼叫端負責同步。 */
public final class MessageWriter {

    private final Writer out;

    public MessageWriter(Writer out) {
        this.out = out;
    }

    public void write(Message message) throws IOException {
        out.write(MessageCodec.encode(message));
        out.write('\n');
        out.flush();
    }
}
