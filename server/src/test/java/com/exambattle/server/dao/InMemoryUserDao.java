package com.exambattle.server.dao;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 測試用的 UserDao：資料存在記憶體，帳號不分大小寫（與資料庫的 utf8mb4_unicode_ci 一致）。
 * 透過 server 的 test-jar 分享給其他模組的測試。
 */
public final class InMemoryUserDao implements UserDao {

    private final Map<String, UserRecord> byUsername = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @Override
    public UserRecord create(NewUser user) throws UsernameTakenException {
        String key = user.username().toLowerCase(Locale.ROOT);
        UserRecord record = new UserRecord(nextId.getAndIncrement(), user.username(), user.displayName(),
                user.passwordHash(), user.passwordSalt(), user.passwordIterations());
        if (byUsername.putIfAbsent(key, record) != null) {
            throw new UsernameTakenException(user.username());
        }
        return record;
    }

    @Override
    public Optional<UserRecord> findByUsername(String username) {
        return Optional.ofNullable(byUsername.get(username.toLowerCase(Locale.ROOT)));
    }

    /** 測試用：把帳號設為管理員（正式環境以 SQL 設定）。 */
    public void grantAdmin(String username) {
        byUsername.computeIfPresent(username.toLowerCase(Locale.ROOT), (k, u) -> new UserRecord(u.id(), u.username(),
                u.displayName(), u.passwordHash(), u.passwordSalt(), u.passwordIterations(), true));
    }

    @Override
    public Optional<UserRecord> findById(long id) {
        return byUsername.values().stream().filter(u -> u.id() == id).findFirst();
    }
}
