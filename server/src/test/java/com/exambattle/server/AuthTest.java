package com.exambattle.server;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.payload.ChatRequest;
import com.exambattle.common.protocol.payload.LoginOk;
import com.exambattle.common.protocol.payload.LoginRequest;
import com.exambattle.common.protocol.payload.RegisterRequest;
import com.exambattle.common.protocol.payload.UserInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** 註冊、登入與已登入名單。 */
class AuthTest extends ServerTestBase {

    @Test
    void registerReturnsNewUser() throws Exception {
        TestClient c = connect();
        c.hello();

        UserInfo alice = c.register("alice", "  Alice 王  ");
        UserInfo bob = c.register("bob", "Bob");

        assertEquals("alice", alice.username());
        assertEquals("Alice 王", alice.displayName());
        assertNotEquals(alice.userId(), bob.userId());
    }

    @Test
    void duplicateUsernameIsRejectedIgnoringCase() throws Exception {
        TestClient c = connect();
        c.hello();
        c.register("alice", "Alice");

        c.expectError(MessageType.REGISTER, new RegisterRequest("ALICE", TestClient.PASSWORD, "A"),
                ErrorCode.USERNAME_TAKEN);
    }

    @Test
    void invalidRegistrationFieldsAreRejected() throws Exception {
        TestClient c = connect();
        c.hello();

        for (RegisterRequest bad : List.of(
                new RegisterRequest("ab", TestClient.PASSWORD, "A"),
                new RegisterRequest("has space", TestClient.PASSWORD, "A"),
                new RegisterRequest("a".repeat(21), TestClient.PASSWORD, "A"),
                new RegisterRequest("alice", "short", "A"),
                new RegisterRequest("alice", "x".repeat(65), "A"),
                new RegisterRequest("alice", TestClient.PASSWORD, "   "),
                new RegisterRequest("alice", TestClient.PASSWORD, "x".repeat(33)),
                new RegisterRequest(null, TestClient.PASSWORD, "A"))) {
            c.expectError(MessageType.REGISTER, bad, ErrorCode.INVALID_PAYLOAD);
        }
    }

    @Test
    void loginReturnsUserAndOnlineList() throws Exception {
        TestClient bob = loggedIn("bob");
        TestClient alice = connect();
        alice.hello();
        alice.register("alice", "Alice");

        LoginOk ok = alice.login("ALICE");

        assertEquals("alice", ok.user().username());
        assertEquals(List.of("bob"), ok.online().stream().map(UserInfo::username).toList());
        UserInfo joined = bob.nextOfType(MessageType.CLIENT_JOINED).payloadAs(UserInfo.class);
        assertEquals(ok.user(), joined);
        assertEquals(2, server.onlineCount());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveSameError() throws Exception {
        TestClient c = connect();
        c.hello();
        c.register("alice", "Alice");

        String wrongPassword = c.expectError(MessageType.LOGIN, new LoginRequest("alice", "wrong-password"),
                ErrorCode.INVALID_CREDENTIALS).message();
        String unknownUser = c.expectError(MessageType.LOGIN, new LoginRequest("nobody", TestClient.PASSWORD),
                ErrorCode.INVALID_CREDENTIALS).message();

        assertEquals(wrongPassword, unknownUser, "錯誤訊息不可透露帳號是否存在");
    }

    @Test
    void missingLoginFieldsAreInvalidPayload() throws Exception {
        TestClient c = connect();
        c.hello();

        c.expectError(MessageType.LOGIN, new LoginRequest("alice", null), ErrorCode.INVALID_PAYLOAD);
    }

    @Test
    void sameAccountCannotLoginTwice() throws Exception {
        TestClient first = loggedIn("alice");
        TestClient second = connect();
        second.hello();

        second.expectError(MessageType.LOGIN, new LoginRequest("alice", TestClient.PASSWORD),
                ErrorCode.ALREADY_LOGGED_IN);
        first.expectError(MessageType.LOGIN, new LoginRequest("alice", TestClient.PASSWORD),
                ErrorCode.ALREADY_LOGGED_IN);
        first.expectError(MessageType.REGISTER, new RegisterRequest("other", TestClient.PASSWORD, "O"),
                ErrorCode.ALREADY_LOGGED_IN);
    }

    @Test
    void accountCanLoginAgainAfterDisconnect() throws Exception {
        TestClient first = loggedIn("alice");
        TestClient watcher = loggedIn("bob");
        first.close();
        watcher.nextOfType(MessageType.CLIENT_LEFT);

        TestClient second = connect();
        second.hello();

        assertEquals("alice", second.login("alice").user().username());
    }

    @Test
    void roomAndChatRequireLogin() throws Exception {
        TestClient c = connect();
        c.hello();

        c.expectError(MessageType.CHAT, new ChatRequest("hi"), ErrorCode.NOT_LOGGED_IN);
        c.expectError(MessageType.CREATE_ROOM, null, ErrorCode.NOT_LOGGED_IN);
        c.expectError(MessageType.GET_ROOM, null, ErrorCode.NOT_LOGGED_IN);
        assertEquals(0, server.onlineCount());
    }
}
