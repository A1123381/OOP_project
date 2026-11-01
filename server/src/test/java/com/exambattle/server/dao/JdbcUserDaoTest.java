package com.exambattle.server.dao;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 對真正的 MariaDB 測試 JdbcUserDao。只有設定 EXAMBATTLE_TEST_DB_URL 時才執行，
 * 請指向測試用資料庫（例如 exam_battle_test），見 docs/DATABASE.md。
 * 只刪除本測試建立的帳號。
 */
@EnabledIfEnvironmentVariable(named = "EXAMBATTLE_TEST_DB_URL", matches = ".+")
class JdbcUserDaoTest {

    private Database database;
    private JdbcUserDao dao;
    private String prefix;

    @BeforeEach
    void connect() {
        DbConfig config = new DbConfig(System.getenv("EXAMBATTLE_TEST_DB_URL"),
                System.getenv("EXAMBATTLE_TEST_DB_USER"),
                System.getenv().getOrDefault("EXAMBATTLE_TEST_DB_PASSWORD", ""));
        database = new Database(config);
        database.verify();
        dao = new JdbcUserDao(database);
        prefix = "t_" + UUID.randomUUID().toString().substring(0, 8) + "_";
    }

    @AfterEach
    void cleanUp() throws Exception {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE username LIKE ?")) {
            ps.setString(1, prefix + "%");
            ps.executeUpdate();
        }
    }

    @Test
    void createAndFindIgnoringCase() throws Exception {
        NewUser user = new NewUser(prefix + "alice", "Alice 王", new byte[32], new byte[16], 1_000);

        UserRecord created = dao.create(user);
        UserRecord found = dao.findByUsername((prefix + "ALICE").toUpperCase()).orElseThrow();

        assertTrue(created.id() > 0);
        assertEquals(created.id(), found.id());
        assertEquals("Alice 王", found.displayName());
        assertArrayEquals(new byte[32], found.passwordHash());
        assertEquals(1_000, found.passwordIterations());
    }

    @Test
    void duplicateUsernameIgnoringCaseIsRejected() throws Exception {
        dao.create(new NewUser(prefix + "bob", "Bob", new byte[32], new byte[16], 1));

        assertThrows(UsernameTakenException.class,
                () -> dao.create(new NewUser((prefix + "BOB").toUpperCase(), "B", new byte[32], new byte[16], 1)));
    }

    @Test
    void unknownUserIsEmpty() {
        assertTrue(dao.findByUsername(prefix + "nobody").isEmpty());
    }
}
