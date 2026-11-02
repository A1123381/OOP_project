package com.exambattle.server.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

/** 以 MariaDB 實作的 {@link UserDao}；一律使用 PreparedStatement。 */
public final class JdbcUserDao implements UserDao {

    /** MariaDB／MySQL 錯誤碼 1062：唯一鍵重複。 */
    private static final int ER_DUP_ENTRY = 1062;

    private static final String INSERT = """
            INSERT INTO users (username, display_name, password_hash, password_salt, password_iterations)
            VALUES (?, ?, ?, ?, ?)""";

    private static final String SELECT_COLUMNS =
            "SELECT id, username, display_name, password_hash, password_salt, password_iterations, is_admin FROM users ";

    private final Database database;

    public JdbcUserDao(Database database) {
        this.database = database;
    }

    @Override
    public UserRecord create(NewUser user) throws UsernameTakenException {
        try (Connection c = database.connection();
             PreparedStatement ps = c.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.username());
            ps.setString(2, user.displayName());
            ps.setBytes(3, user.passwordHash());
            ps.setBytes(4, user.passwordSalt());
            ps.setInt(5, user.passwordIterations());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new DataAccessException("新增帳號後沒有取得 id", null);
                }
                return new UserRecord(keys.getLong(1), user.username(), user.displayName(),
                        user.passwordHash(), user.passwordSalt(), user.passwordIterations());
            }
        } catch (SQLException e) {
            // 依錯誤碼判斷，不依例外型別：不同驅動對唯一鍵衝突丟出的子類別不一定相同
            if (e.getErrorCode() == ER_DUP_ENTRY) {
                throw new UsernameTakenException(user.username());
            }
            throw new DataAccessException("新增帳號失敗", e);
        }
    }

    @Override
    public Optional<UserRecord> findByUsername(String username) {
        return findOne("WHERE username = ?", ps -> ps.setString(1, username));
    }

    @Override
    public Optional<UserRecord> findById(long id) {
        return findOne("WHERE id = ?", ps -> ps.setLong(1, id));
    }

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private Optional<UserRecord> findOne(String where, Binder binder) {
        return database.run("查詢帳號", c -> {
            try (PreparedStatement ps = c.prepareStatement(SELECT_COLUMNS + where)) {
                binder.bind(ps);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    return Optional.of(new UserRecord(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("display_name"),
                            rs.getBytes("password_hash"),
                            rs.getBytes("password_salt"),
                            rs.getInt("password_iterations"),
                            rs.getBoolean("is_admin")));
                }
            }
        });
    }
}
