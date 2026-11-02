package com.exambattle.server.handler;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.ProtocolException;
import com.exambattle.common.protocol.payload.LoginRequest;
import com.exambattle.common.protocol.payload.RegisterRequest;
import com.exambattle.common.protocol.payload.UserInfo;
import com.exambattle.server.ClientRegistry;
import com.exambattle.server.Session;
import com.exambattle.server.auth.AuthService;
import com.exambattle.server.dao.UsernameTakenException;

import java.util.Optional;

/**
 * 處理 REGISTER 與 LOGIN。資料庫查詢與密碼雜湊在鎖外執行，
 * 只有最後加入已登入名單時才進入 ClientRegistry 的鎖。
 */
public final class AuthHandler {

    private final AuthService auth;
    private final ClientRegistry registry;

    public AuthHandler(AuthService auth, ClientRegistry registry) {
        this.auth = auth;
        this.registry = registry;
    }

    public void register(Session session, Message message) throws ProtocolException {
        requireNotLoggedIn(session, message);
        RegisterRequest request = message.payloadAs(RegisterRequest.class);
        String displayName = PayloadValidator.register(request, message.requestId());
        try {
            UserInfo user = auth.register(request.username(), request.password(), displayName);
            session.send(Message.of(MessageType.REGISTER_OK, message.requestId(), user));
        } catch (UsernameTakenException e) {
            throw new ProtocolException(ErrorCode.USERNAME_TAKEN, "帳號 " + request.username() + " 已被使用",
                    message.requestId());
        }
    }

    public void login(Session session, Message message) throws ProtocolException {
        requireNotLoggedIn(session, message);
        LoginRequest request = message.payloadAs(LoginRequest.class);
        if (request.username() == null || request.password() == null) {
            throw PayloadValidator.invalid("缺少 username 或 password", message.requestId());
        }
        Optional<UserInfo> user = auth.authenticate(request.username(), request.password());
        if (user.isEmpty()) {
            throw new ProtocolException(ErrorCode.INVALID_CREDENTIALS, "帳號或密碼錯誤", message.requestId());
        }
        if (!registry.tryLogin(session, user.get(), auth.isAdmin(user.get().userId()), message.requestId())) {
            throw new ProtocolException(ErrorCode.ALREADY_LOGGED_IN, "此帳號已在其他地方登入", message.requestId());
        }
    }

    private static void requireNotLoggedIn(Session session, Message message) throws ProtocolException {
        if (session.user() != null) {
            throw new ProtocolException(ErrorCode.ALREADY_LOGGED_IN, "你已經登入", message.requestId());
        }
    }
}
