package com.exambattle.common.io;

import java.io.IOException;
import java.io.Reader;

/**
 * 逐行讀取並限制單行長度，避免對方送出超長的行耗盡記憶體。
 * 行尾可以是 \n 或 \r\n。呼叫端應傳入有緩衝的 Reader。
 */
public final class BoundedLineReader {

    private final Reader in;
    private final int maxChars;

    public BoundedLineReader(Reader in, int maxChars) {
        this.in = in;
        this.maxChars = maxChars;
    }

    /** 回傳下一行（不含行尾）；串流結束時回傳 null。 */
    public String readLine() throws IOException {
        StringBuilder line = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') {
                int last = line.length() - 1;
                if (last >= 0 && line.charAt(last) == '\r') {
                    line.setLength(last);
                }
                if (line.length() > maxChars) {
                    throw new LineTooLongException(maxChars);
                }
                return line.toString();
            }
            // 多容許一個字元，留給 \r\n 的 \r
            if (line.length() > maxChars) {
                throw new LineTooLongException(maxChars);
            }
            line.append((char) c);
        }
        if (line.isEmpty()) {
            return null;
        }
        if (line.length() > maxChars) {
            throw new LineTooLongException(maxChars);
        }
        return line.toString();
    }
}
