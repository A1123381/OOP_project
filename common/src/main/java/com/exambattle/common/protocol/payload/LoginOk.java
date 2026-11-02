package com.exambattle.common.protocol.payload;

import java.util.List;

/** LOGIN_OK：登入成功，附上目前其他在線的使用者；admin 表示管理員（第八階段）。 */
public record LoginOk(UserInfo user, List<UserInfo> online, boolean admin) {

    public LoginOk(UserInfo user, List<UserInfo> online) {
        this(user, online, false);
    }
}
