package com.exambattle.server.room;

import com.exambattle.common.protocol.Protocol;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Random;
import java.util.function.Predicate;

/** 產生 6 碼房間代碼，字元取自 {@link Protocol#ROOM_CODE_ALPHABET}。 */
public final class RoomCodeGenerator {

    private final Random random;

    public RoomCodeGenerator() {
        this(new SecureRandom());
    }

    /** 測試時可注入固定種子的 Random。 */
    public RoomCodeGenerator(Random random) {
        this.random = random;
    }

    /** 產生一個 isTaken 回傳 false 的代碼。 */
    public String next(Predicate<String> isTaken) {
        String alphabet = Protocol.ROOM_CODE_ALPHABET;
        while (true) {
            StringBuilder code = new StringBuilder(Protocol.ROOM_CODE_LENGTH);
            for (int i = 0; i < Protocol.ROOM_CODE_LENGTH; i++) {
                code.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            String candidate = code.toString();
            if (!isTaken.test(candidate)) {
                return candidate;
            }
        }
    }

    /** 使用者輸入的代碼轉成標準格式（去空白、轉大寫）。 */
    public static String normalize(String input) {
        return input == null ? "" : input.strip().toUpperCase(Locale.ROOT);
    }
}
