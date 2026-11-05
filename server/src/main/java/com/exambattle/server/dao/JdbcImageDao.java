package com.exambattle.server.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 以 MariaDB 實作的 {@link ImageDao}。時間以 UTC 寫入 DATETIME(3)。 */
public final class JdbcImageDao implements ImageDao {

    private final Database database;

    public JdbcImageDao(Database database) {
        this.database = database;
    }

    @Override
    public ImageRecord saveOrGet(ImageRecord image) {
        return database.inTransaction("儲存圖片", c -> {
            Optional<ImageRecord> existing = findOne(c, "sha256", image.sha256());
            if (existing.isPresent()) {
                return existing.get();
            }
            try (PreparedStatement ps = c.prepareStatement("""
                    INSERT INTO images (id, sha256, mime, width, height, bytes, created_by, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")) {
                ps.setString(1, image.id());
                ps.setString(2, image.sha256());
                ps.setString(3, image.mime());
                ps.setInt(4, image.width());
                ps.setInt(5, image.height());
                ps.setBytes(6, image.bytes());
                ps.setLong(7, image.createdBy());
                ps.setObject(8, LocalDateTime.ofInstant(image.createdAt(), ZoneOffset.UTC));
                ps.executeUpdate();
            }
            return image;
        });
    }

    @Override
    public Optional<ImageRecord> find(String id) {
        return database.run("讀取圖片", c -> findOne(c, "id", id));
    }

    @Override
    public Set<String> existing(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Set.of();
        }
        List<String> list = List.copyOf(new HashSet<>(ids));
        String placeholders = String.join(",", Collections.nCopies(list.size(), "?"));
        return database.run("查詢圖片", c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM images WHERE id IN (" + placeholders + ")")) {
                for (int i = 0; i < list.size(); i++) {
                    ps.setString(i + 1, list.get(i));
                }
                try (ResultSet rs = ps.executeQuery()) {
                    Set<String> found = new HashSet<>();
                    while (rs.next()) {
                        found.add(rs.getString(1));
                    }
                    return found;
                }
            }
        });
    }

    @Override
    public int deleteUnreferencedBefore(Instant cutoff) {
        return database.run("清除未使用的圖片", c -> {
            try (PreparedStatement ps = c.prepareStatement("""
                    DELETE FROM images WHERE created_at < ?
                    AND NOT EXISTS (SELECT 1 FROM questions q WHERE q.image_id = images.id)
                    AND NOT EXISTS (SELECT 1 FROM question_options o WHERE o.image_id = images.id)
                    AND NOT EXISTS (SELECT 1 FROM question_groups g WHERE g.image_id = images.id)""")) {
                ps.setObject(1, LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC));
                return ps.executeUpdate();
            }
        });
    }

    private static Optional<ImageRecord> findOne(Connection c, String column, String value) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id, sha256, mime, width, height, bytes, created_by,"
                + " created_at FROM images WHERE " + column + " = ?")) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ImageRecord(rs.getString("id"), rs.getString("sha256"), rs.getString("mime"),
                        rs.getInt("width"), rs.getInt("height"), rs.getBytes("bytes"), rs.getLong("created_by"),
                        rs.getObject("created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC)));
            }
        }
    }
}
