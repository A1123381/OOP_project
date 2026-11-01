package com.exambattle.common.protocol.payload;

import java.util.List;

/** LOGIN_OK：登入成功，附上目前其他在線的使用者。 */
public record LoginOk(UserInfo user, List<UserInfo> online) {
}
