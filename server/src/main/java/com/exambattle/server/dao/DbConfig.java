package com.exambattle.server.dao;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * 資料庫連線設定。來源優先順序：環境變數 → 設定檔 → 錯誤。
 *
 * <ul>
 *   <li>環境變數：{@code EXAMBATTLE_DB_URL}、{@code EXAMBATTLE_DB_USER}、{@code EXAMBATTLE_DB_PASSWORD}</li>
 *   <li>設定檔（預設 {@code config/db.properties}，不進版本控制）：{@code db.url}、{@code db.user}、{@code db.password}</li>
 * </ul>
 */
public record DbConfig(String url, String user, String password) {

    public static final Path DEFAULT_FILE = Path.of("config", "db.properties");

    /** 依環境變數與設定檔載入；缺少 url 或 user 時丟出 IllegalStateException。 */
    public static DbConfig load(Map<String, String> env, Path file) throws IOException {
        Properties props = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
        }
        String url = pick(env, "EXAMBATTLE_DB_URL", props, "db.url");
        String user = pick(env, "EXAMBATTLE_DB_USER", props, "db.user");
        String password = pick(env, "EXAMBATTLE_DB_PASSWORD", props, "db.password");
        if (url == null || user == null) {
            throw new IllegalStateException("缺少資料庫設定：請建立 " + file
                    + "（可複製 config/db.properties.example）或設定環境變數 EXAMBATTLE_DB_URL / EXAMBATTLE_DB_USER / EXAMBATTLE_DB_PASSWORD");
        }
        return new DbConfig(url, user, password == null ? "" : password);
    }

    private static String pick(Map<String, String> env, String envKey, Properties props, String propKey) {
        String value = env.get(envKey);
        if (value == null || value.isBlank()) {
            value = props.getProperty(propKey);
        }
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** 不輸出密碼。 */
    @Override
    public String toString() {
        return "DbConfig[url=" + url + ", user=" + user + "]";
    }
}
