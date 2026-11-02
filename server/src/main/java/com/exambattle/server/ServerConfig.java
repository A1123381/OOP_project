package com.exambattle.server;

import com.exambattle.common.protocol.Protocol;
import com.exambattle.server.auth.PasswordHasher;
import com.exambattle.server.game.GameTiming;

import java.time.Duration;

/**
 * 伺服器設定。
 *
 * @param port               監聽埠號；0 表示由系統挑選（測試用）
 * @param idleTimeout        連線多久沒有收到任何訊息就判定逾時
 * @param maxRoomPlayers     每個房間的人數上限
 * @param passwordIterations 新密碼的 PBKDF2 迭代次數（測試時可調低以加快速度）
 * @param gameTiming         多人競賽的時間設定（測試時可縮短）
 */
public record ServerConfig(int port, Duration idleTimeout, int maxRoomPlayers, int passwordIterations,
                           GameTiming gameTiming) {

    public ServerConfig(int port, Duration idleTimeout, int maxRoomPlayers, int passwordIterations) {
        this(port, idleTimeout, maxRoomPlayers, passwordIterations, GameTiming.DEFAULT);
    }

    public static ServerConfig defaults() {
        return new ServerConfig(Protocol.DEFAULT_PORT, Protocol.SERVER_IDLE_TIMEOUT,
                Protocol.DEFAULT_MAX_ROOM_PLAYERS, PasswordHasher.DEFAULT_ITERATIONS);
    }

    public ServerConfig withPort(int newPort) {
        return new ServerConfig(newPort, idleTimeout, maxRoomPlayers, passwordIterations, gameTiming);
    }

    public ServerConfig withIdleTimeout(Duration newIdleTimeout) {
        return new ServerConfig(port, newIdleTimeout, maxRoomPlayers, passwordIterations, gameTiming);
    }

    public ServerConfig withMaxRoomPlayers(int newMax) {
        return new ServerConfig(port, idleTimeout, newMax, passwordIterations, gameTiming);
    }

    public ServerConfig withPasswordIterations(int newIterations) {
        return new ServerConfig(port, idleTimeout, maxRoomPlayers, newIterations, gameTiming);
    }

    public ServerConfig withGameTiming(GameTiming newTiming) {
        return new ServerConfig(port, idleTimeout, maxRoomPlayers, passwordIterations, newTiming);
    }
}
