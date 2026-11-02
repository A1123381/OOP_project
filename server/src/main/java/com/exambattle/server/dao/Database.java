package com.exambattle.server.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** 依 {@link DbConfig} 取得 JDBC 連線。目前不使用連線池，每次操作開一條連線。 */
public final class Database {

    private static final int VALIDATION_TIMEOUT_SECONDS = 5;

    /** 在一條連線上執行的資料庫工作。 */
    @FunctionalInterface
    public interface SqlWork<T> {
        T run(Connection connection) throws SQLException;
    }

    private final DbConfig config;

    public Database(DbConfig config) {
        this.config = config;
    }

    public Connection connection() throws SQLException {
        return DriverManager.getConnection(config.url(), config.user(), config.password());
    }

    /** 在單一連線上執行（自動提交）；SQLException 轉成 DataAccessException。 */
    public <T> T run(String action, SqlWork<T> work) {
        try (Connection c = connection()) {
            return work.run(c);
        } catch (SQLException e) {
            throw new DataAccessException(action + "失敗", e);
        }
    }

    /** 在交易內執行，成功時提交、任何例外時回滾；SQLException 轉成 DataAccessException。 */
    public <T> T inTransaction(String action, SqlWork<T> work) {
        try (Connection c = connection()) {
            c.setAutoCommit(false);
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException(action + "失敗", e);
        }
    }

    /** 啟動時檢查能否連線，並確認所有資料表都存在。 */
    public void verify() {
        try (Connection c = connection()) {
            if (!c.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                throw new DataAccessException("資料庫連線無效：" + config, null);
            }
            for (String table : new String[] {"users", "subjects", "exams", "questions", "question_options",
                    "exam_questions", "practice_records", "answer_records", "wrong_questions", "question_groups"}) {
                try (var rs = c.getMetaData().getTables(c.getCatalog(), null, table, null)) {
                    if (!rs.next()) {
                        throw new DataAccessException("找不到 " + table + " 資料表，請先執行 db/schema.sql"
                                + "（既有資料庫請執行 db/migrations/ 中尚未執行的檔案）", null);
                    }
                }
            }
            requireColumn(c, "questions", "group_id", "001_question_groups.sql");
            requireColumn(c, "exams", "visibility", "002_visibility_admin.sql");
            requireColumn(c, "users", "is_admin", "002_visibility_admin.sql");
        } catch (SQLException e) {
            throw new DataAccessException("無法連線到資料庫 " + config + "：" + e.getMessage(), e);
        }
    }

    /** 升級後新增的欄位；缺少時提示要執行的 migration。 */
    private static void requireColumn(Connection c, String table, String column, String migration) throws SQLException {
        try (var rs = c.getMetaData().getColumns(c.getCatalog(), null, table, column)) {
            if (!rs.next()) {
                throw new DataAccessException(table + " 資料表缺少 " + column + " 欄位，請執行 db/migrations/" + migration, null);
            }
        }
    }
}
