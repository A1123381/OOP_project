package com.exambattle.server;

import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.payload.ClientLeftPayload;
import com.exambattle.common.protocol.payload.LeaveReason;
import com.exambattle.common.protocol.payload.LoginOk;
import com.exambattle.common.protocol.payload.UserInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 已登入的使用者名單（以 userId 為鍵），負責登入、離線與上下線通知。
 *
 * <p>與 {@link com.exambattle.server.room.RoomManager} 共用同一把鎖（見 DECISIONS.md D-008），
 * 所以登入、房間與聊天事件在每個客戶端看到的順序一致。
 */
public final class ClientRegistry {

    private final Object lock;
    private final Map<Long, Session> sessions = new LinkedHashMap<>();
    private boolean shuttingDown;

    public ClientRegistry(Object lock) {
        this.lock = lock;
    }

    /**
     * 登入：送出 LOGIN_OK 給本人、CLIENT_JOINED 給其他人。
     *
     * @return false 表示此帳號已在其他連線登入
     */
    public boolean tryLogin(Session session, UserInfo user, boolean admin, String requestId) {
        synchronized (lock) {
            if (sessions.containsKey(user.userId())) {
                return false;
            }
            List<UserInfo> others = sessions.values().stream().map(Session::user).toList();
            session.bindUser(user);
            session.send(Message.of(MessageType.LOGIN_OK, requestId, new LoginOk(user, others, admin)));
            Message joined = Message.of(MessageType.CLIENT_JOINED, null, user);
            sessions.values().forEach(s -> s.send(joined));
            sessions.put(user.userId(), session);
            return true;
        }
    }

    /** 已登入者離線時呼叫；未登入的連線不會廣播。 */
    public void logout(Session session, LeaveReason reason) {
        synchronized (lock) {
            UserInfo user = session.user();
            if (user == null || sessions.get(user.userId()) != session) {
                return;
            }
            sessions.remove(user.userId());
            if (shuttingDown) {
                return;
            }
            Message left = Message.of(MessageType.CLIENT_LEFT, null, new ClientLeftPayload(user, reason));
            sessions.values().forEach(s -> s.send(left));
        }
    }

    /** 目前已登入的連線快照，依登入順序排列。 */
    public List<Session> sessions() {
        synchronized (lock) {
            return new ArrayList<>(sessions.values());
        }
    }

    /** 進入關閉流程後，離線不再廣播 CLIENT_LEFT。 */
    public void beginShutdown() {
        synchronized (lock) {
            shuttingDown = true;
        }
    }

    public int size() {
        synchronized (lock) {
            return sessions.size();
        }
    }
}
