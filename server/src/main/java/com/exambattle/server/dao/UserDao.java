package com.exambattle.server.dao;

import java.util.Optional;

/** users 資料表的存取介面。實作：{@link JdbcUserDao}（MariaDB）。 */
public interface UserDao {

    /**
     * 新增帳號並回傳含 id 的紀錄。
     *
     * @throws UsernameTakenException 帳號已存在（不分大小寫）
     * @throws DataAccessException    資料庫錯誤
     */
    UserRecord create(NewUser user) throws UsernameTakenException;

    /** 以帳號查詢（不分大小寫）。 */
    Optional<UserRecord> findByUsername(String username);

    Optional<UserRecord> findById(long id);
}
