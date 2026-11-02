package com.exambattle.server;

import com.exambattle.server.dao.TestRepositories;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 每個測試啟動一台使用記憶體 DAO 的伺服器（隨機埠、低迭代次數、含初始分類）。 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
abstract class ServerTestBase {

    /** 測試用的 PBKDF2 迭代次數，避免測試變慢。 */
    static final int TEST_ITERATIONS = 1_000;

    ChatServer server;
    private final List<TestClient> clients = new ArrayList<>();

    @BeforeEach
    void startDefaultServer() throws Exception {
        restartServer(testConfig());
    }

    static ServerConfig testConfig() {
        return new ServerConfig(0, Duration.ofSeconds(10), 50, TEST_ITERATIONS);
    }

    void restartServer(ServerConfig config) throws Exception {
        restartServer(config, TestRepositories.inMemory());
    }

    void restartServer(ServerConfig config, com.exambattle.server.dao.Repositories repos) throws Exception {
        if (server != null) {
            server.close();
        }
        server = new ChatServer(config, repos);
        server.start();
    }

    @AfterEach
    void stopServer() throws Exception {
        for (TestClient c : clients) {
            c.close();
        }
        server.close();
    }

    TestClient connect() throws Exception {
        TestClient c = new TestClient(server.port());
        clients.add(c);
        return c;
    }

    /** 連線並完成註冊與登入。 */
    TestClient loggedIn(String username) throws Exception {
        TestClient c = connect();
        c.signUpAndLogin(username);
        return c;
    }
}
