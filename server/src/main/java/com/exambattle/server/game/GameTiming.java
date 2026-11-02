package com.exambattle.server.game;

import java.time.Duration;

/**
 * 競賽的時間設定（D-037）。
 *
 * @param second      「一秒」的長度；正式為 1 秒，測試時縮短以加快速度
 * @param startDelay  GAME_STARTED 之後多久送出第一題
 * @param resultPause 公布一題結果之後多久送出下一題（或 GAME_OVER）
 * @param grace       截止後仍接受答案的寬限時間（網路延遲）
 */
public record GameTiming(Duration second, Duration startDelay, Duration resultPause, Duration grace) {

    public static final GameTiming DEFAULT = new GameTiming(Duration.ofSeconds(1), Duration.ofSeconds(3),
            Duration.ofSeconds(5), Duration.ofMillis(500));

    /** 每題的作答時間。 */
    public Duration answerTime(int seconds) {
        return second.multipliedBy(seconds);
    }
}
