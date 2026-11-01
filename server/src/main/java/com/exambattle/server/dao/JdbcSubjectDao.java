package com.exambattle.server.dao;

import com.exambattle.server.dao.BankRecords.SubjectRecord;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 以 MariaDB 實作的 {@link SubjectDao}。 */
public final class JdbcSubjectDao implements SubjectDao {

    private static final int ER_DUP_ENTRY = 1062;

    private final Database database;

    public JdbcSubjectDao(Database database) {
        this.database = database;
    }

    @Override
    public List<SubjectRecord> list() {
        return database.run("查詢分類", c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT id, name FROM subjects ORDER BY name, id");
                 ResultSet rs = ps.executeQuery()) {
                List<SubjectRecord> subjects = new ArrayList<>();
                while (rs.next()) {
                    subjects.add(new SubjectRecord(rs.getLong("id"), rs.getString("name")));
                }
                return subjects;
            }
        });
    }

    @Override
    public Optional<SubjectRecord> findById(long id) {
        return database.run("查詢分類", c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT id, name FROM subjects WHERE id = ?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next()
                            ? Optional.of(new SubjectRecord(rs.getLong("id"), rs.getString("name")))
                            : Optional.empty();
                }
            }
        });
    }

    @Override
    public SubjectRecord create(String name, long createdBy) throws SubjectExistsException {
        try {
            return database.run("新增分類", c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO subjects (name, created_by) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, name);
                    ps.setLong(2, createdBy);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        return new SubjectRecord(keys.getLong(1), name);
                    }
                }
            });
        } catch (DataAccessException e) {
            if (e.getCause() instanceof SQLException sql && sql.getErrorCode() == ER_DUP_ENTRY) {
                throw new SubjectExistsException(name);
            }
            throw e;
        }
    }
}
