package com.exambattle.common.protocol;

import java.time.Duration;
import java.util.regex.Pattern;

/** 協定常數，對應 docs/PROTOCOL.md。 */
public final class Protocol {

    public static final int VERSION = 1;
    public static final int DEFAULT_PORT = 5555;
    /** 單行上限（字元）；第七階段為了整批匯入由 65,536 調高（見 DECISIONS.md D-031）。 */
    public static final int MAX_LINE_CHARS = 1_048_576;
    public static final int MAX_CHAT_CHARS = 1_000;
    public static final Duration CLIENT_PING_INTERVAL = Duration.ofSeconds(15);
    public static final Duration SERVER_IDLE_TIMEOUT = Duration.ofSeconds(45);

    /** 帳號：3–20 個英數字或底線。 */
    public static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,20}");
    public static final int MIN_PASSWORD_CHARS = 8;
    public static final int MAX_PASSWORD_CHARS = 64;
    public static final int MAX_DISPLAY_NAME_CHARS = 32;

    /** 房間代碼長度與可用字元（去除易混淆的 0/O、1/I）。 */
    public static final int ROOM_CODE_LENGTH = 6;
    public static final String ROOM_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    public static final int DEFAULT_MAX_ROOM_PLAYERS = 50;

    /** 題庫欄位上限，與 db/schema.sql 的欄位長度一致。 */
    public static final int MAX_SUBJECT_NAME_CHARS = 50;
    public static final int MAX_EXAM_TITLE_CHARS = 200;
    public static final int MAX_EXAM_TYPE_CHARS = 50;
    public static final int MIN_EXAM_YEAR = 1900;
    public static final int MAX_EXAM_YEAR = 2100;
    public static final int MAX_QUESTION_CHARS = 5_000;
    public static final int MAX_EXPLANATION_CHARS = 5_000;
    public static final int MAX_OPTION_CHARS = 1_000;
    public static final int MIN_OPTIONS = 2;
    public static final int MAX_OPTIONS = 10;
    /** 考卷搜尋關鍵字的長度上限（第八階段）。 */
    public static final int MAX_SEARCH_KEYWORD_CHARS = 50;
    /** 題組文章（閱讀測驗、克漏字），與 question_groups.passage 一致。 */
    public static final int MAX_PASSAGE_CHARS = 20_000;

    /** 單人練習的題數。 */
    public static final int DEFAULT_PRACTICE_COUNT = 10;
    public static final int MAX_PRACTICE_COUNT = 100;

    /** 多人競賽（第四階段，D-037）。 */
    public static final java.util.List<Integer> GAME_SECONDS_CHOICES = java.util.List.of(10, 20, 30, 60);
    public static final int DEFAULT_GAME_COUNT = 10;
    public static final int MAX_GAME_QUESTIONS = 100;
    /** 每題基本分；多選題依學測規則乘上得分比例。 */
    public static final int GAME_BASE_POINTS = 1000;
    /** 連續答對加成：第 2 題起每多連續 1 題加 100 分，最多 500 分。 */
    public static final int GAME_STREAK_STEP = 100;
    public static final int GAME_STREAK_MAX_BONUS = 500;

    /** 一次 IMPORT_QUESTIONS 最多的題數。 */
    public static final int MAX_IMPORT_QUESTIONS = 200;

    private Protocol() {
    }
}
