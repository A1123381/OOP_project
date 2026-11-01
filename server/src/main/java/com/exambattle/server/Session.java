package com.exambattle.server;

import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.payload.UserInfo;

/**
 * 一條客戶端連線在伺服器端的抽象，供房間、名單與訊息處理器使用。
 * 實作：{@link ClientHandler}。
 */
public interface Session {

    /** 本次連線的識別碼（只用於日誌）。 */
    String clientId();

    /** 已登入的使用者；尚未登入時為 null。 */
    UserInfo user();

    /** 登入成功時由 {@link ClientRegistry} 在鎖內呼叫。 */
    void bindUser(UserInfo user);

    /** 將訊息排入送出佇列，不會阻塞。 */
    void send(Message message);
}
