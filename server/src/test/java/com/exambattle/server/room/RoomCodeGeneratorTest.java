package com.exambattle.server.room;

import com.exambattle.common.protocol.Protocol;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoomCodeGeneratorTest {

    @Test
    void codesUseAlphabetAndLength() {
        RoomCodeGenerator generator = new RoomCodeGenerator(new Random(42));

        for (int i = 0; i < 1_000; i++) {
            String code = generator.next(c -> false);
            assertEquals(Protocol.ROOM_CODE_LENGTH, code.length());
            assertTrue(code.chars().allMatch(ch -> Protocol.ROOM_CODE_ALPHABET.indexOf(ch) >= 0), code);
        }
    }

    @Test
    void skipsTakenCodes() {
        RoomCodeGenerator generator = new RoomCodeGenerator(new Random(7));
        String first = new RoomCodeGenerator(new Random(7)).next(c -> false);
        Set<String> taken = new HashSet<>(Set.of(first));

        assertNotEquals(first, generator.next(taken::contains));
    }

    @Test
    void normalizeTrimsAndUppercases() {
        assertEquals("ABC234", RoomCodeGenerator.normalize("  abc234 "));
        assertEquals("", RoomCodeGenerator.normalize(null));
    }
}
