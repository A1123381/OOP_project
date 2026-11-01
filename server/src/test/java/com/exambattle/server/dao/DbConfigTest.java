package com.exambattle.server.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DbConfigTest {

    @TempDir
    Path dir;

    @Test
    void readsPropertiesFile() throws Exception {
        Path file = dir.resolve("db.properties");
        Files.writeString(file, "db.url=jdbc:mariadb://h/db\ndb.user=u\ndb.password=p\n");

        assertEquals(new DbConfig("jdbc:mariadb://h/db", "u", "p"), DbConfig.load(Map.of(), file));
    }

    @Test
    void environmentOverridesFile() throws Exception {
        Path file = dir.resolve("db.properties");
        Files.writeString(file, "db.url=jdbc:mariadb://file/db\ndb.user=fileuser\ndb.password=filepw\n");

        DbConfig config = DbConfig.load(Map.of("EXAMBATTLE_DB_URL", "jdbc:mariadb://env/db",
                "EXAMBATTLE_DB_PASSWORD", "envpw"), file);

        assertEquals(new DbConfig("jdbc:mariadb://env/db", "fileuser", "envpw"), config);
    }

    @Test
    void missingSettingsFailWithHelpfulMessage() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> DbConfig.load(Map.of(), dir.resolve("missing.properties")));
        assertFalse(e.getMessage().isBlank());
    }

    @Test
    void toStringHidesPassword() {
        assertFalse(new DbConfig("url", "user", "secret-pw").toString().contains("secret-pw"));
    }
}
