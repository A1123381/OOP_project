package com.exambattle.server.auth;

import com.exambattle.common.protocol.payload.UserInfo;
import com.exambattle.server.dao.NewUser;
import com.exambattle.server.dao.UserDao;
import com.exambattle.server.dao.UserRecord;
import com.exambattle.server.dao.UsernameTakenException;

import java.util.Optional;

/**
 * 註冊與帳密驗證。欄位格式由呼叫端先檢查；這裡只處理雜湊與資料存取。
 * 「是否已在他處登入」由 {@link com.exambattle.server.ClientRegistry} 判斷。
 */
public final class AuthService {

    private final UserDao users;
    private final PasswordHasher hasher;
    /** 帳號不存在時也做一次雜湊，讓回應時間不透露帳號是否存在。 */
    private final PasswordHasher.Hashed dummy;

    public AuthService(UserDao users, PasswordHasher hasher) {
        this.users = users;
        this.hasher = hasher;
        this.dummy = hasher.hash("dummy-password");
    }

    public UserInfo register(String username, String password, String displayName) throws UsernameTakenException {
        PasswordHasher.Hashed hashed = hasher.hash(password);
        UserRecord created = users.create(new NewUser(username, displayName,
                hashed.hash(), hashed.salt(), hashed.iterations()));
        return toInfo(created);
    }

    /** 帳號不存在或密碼錯誤都回傳 empty。 */
    public Optional<UserInfo> authenticate(String username, String password) {
        Optional<UserRecord> found = users.findByUsername(username);
        if (found.isEmpty()) {
            hasher.verify(password, dummy.hash(), dummy.salt(), dummy.iterations());
            return Optional.empty();
        }
        UserRecord user = found.get();
        if (!hasher.verify(password, user.passwordHash(), user.passwordSalt(), user.passwordIterations())) {
            return Optional.empty();
        }
        return Optional.of(toInfo(user));
    }

    /** 是否為管理員（第八階段，D-040）。 */
    public boolean isAdmin(long userId) {
        return users.findById(userId).map(UserRecord::admin).orElse(false);
    }

    private static UserInfo toInfo(UserRecord user) {
        return new UserInfo(user.id(), user.username(), user.displayName());
    }
}
