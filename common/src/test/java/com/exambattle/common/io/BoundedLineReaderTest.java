package com.exambattle.common.io;

import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundedLineReaderTest {

    @Test
    void readsLinesWithLfAndCrLf() throws Exception {
        BoundedLineReader reader = new BoundedLineReader(new StringReader("a\nbb\r\n\nlast"), 10);

        assertEquals("a", reader.readLine());
        assertEquals("bb", reader.readLine());
        assertEquals("", reader.readLine());
        assertEquals("last", reader.readLine());
        assertNull(reader.readLine());
    }

    @Test
    void lineExactlyAtLimitIsAccepted() throws Exception {
        BoundedLineReader reader = new BoundedLineReader(new StringReader("12345\r\n12345"), 5);

        assertEquals("12345", reader.readLine());
        assertEquals("12345", reader.readLine());
    }

    @Test
    void lineOverLimitThrows() {
        BoundedLineReader reader = new BoundedLineReader(new StringReader("123456\n"), 5);

        assertThrows(LineTooLongException.class, reader::readLine);
    }

    @Test
    void unterminatedLineOverLimitThrows() {
        BoundedLineReader reader = new BoundedLineReader(new StringReader("123456"), 5);

        assertThrows(LineTooLongException.class, reader::readLine);
    }

    @Test
    void handlesChineseCharacters() throws Exception {
        BoundedLineReader reader = new BoundedLineReader(new StringReader("考試練習\n"), 4);

        assertEquals("考試練習", reader.readLine());
    }
}
