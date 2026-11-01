package com.exambattle.tools;

import com.exambattle.server.ChatServer;
import com.exambattle.server.ServerConfig;
import com.exambattle.server.dao.TestRepositories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(value = 60, unit = TimeUnit.SECONDS)
class MultiClientLoadTestTest {

    @Test
    void fiftyClientsInFiveRoomsAgainstRealServerPass() throws Exception {
        ServerConfig config = new ServerConfig(0, Duration.ofSeconds(30), 50, 1_000);
        try (ChatServer server = new ChatServer(config, TestRepositories.inMemory())) {
            server.start();

            MultiClientLoadTest.Result result = new MultiClientLoadTest(new MultiClientLoadTest.Options(
                    "127.0.0.1", server.port(), 50, 5, 10, Duration.ofSeconds(30))).run();

            assertTrue(result.passed(), () -> "失敗：" + result.failures());
            assertEquals(50, result.succeeded());
        }
    }

    @Test
    void reportsFailureWhenServerIsDown() throws Exception {
        int unusedPort;
        try (ServerSocket s = new ServerSocket(0)) {
            unusedPort = s.getLocalPort();
        }

        MultiClientLoadTest.Result result = new MultiClientLoadTest(new MultiClientLoadTest.Options(
                "127.0.0.1", unusedPort, 3, 1, 1, Duration.ofSeconds(5))).run();

        assertFalse(result.passed());
        assertEquals(3, result.failures().size());
    }

    @Test
    void roomsMustNotExceedClients() {
        assertThrows(IllegalArgumentException.class, () -> new MultiClientLoadTest.Options(
                "localhost", 1, 2, 3, 1, Duration.ofSeconds(1)));
    }
}
