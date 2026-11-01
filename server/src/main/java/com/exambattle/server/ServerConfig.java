package com.exambattle.server;

import com.exambattle.common.protocol.Protocol;
import com.exambattle.server.auth.PasswordHasher;

import java.time.Duration;

/**
 * 伺服器設定。
 *
 * @param port               監聽埠號；0 表示由系統挑選（測試用）
 * @param idleTimeout        連線多久沒有收到任何訊息就判定逾時
 * @param maxRoomPlayers     每個房間的人數上限
 * @param passwordIterations 新密碼的 PBKDF2 迭代次數（測試時可調低以加快速度）
 */
public record ServerConfig(int port, Duration idleTimeout, int maxRoomPlayers, int passwordIterations) {

    public static ServerConfig defaults() {
        return new ServerConfig(Protocol.DEFAULT_PORT, Protocol.SERVER_IDLE_TIMEOUT,
                Protocol.DEFAULT_MAX_ROOM_PLAYERS, PasswordHasher.DEFAULT_ITERATIONS);
    }

    public ServerConfig withPort(int newPort) {
        return new ServerConfig(newPort, idleTimeout, maxRoomPlayers, passwordIterations);
    }

    public ServerConfig withIdleTimeout(Duration newIdleTimeout) {
        return new ServerConfig(port, newIdleTimeout, maxRoomPlayers, passwordIterations);
    }

    public ServerConfig withMaxRoomPlayers(int newMax) {
        return new ServerConfig(port, idleTimeout, newMax, passwordIterations);
    }

    public ServerConfig withPasswordIterations(int newIterations) {
        return new ServerConfig(port, idleTimeout, maxRoomPlayers, newIterations);
    }
}
