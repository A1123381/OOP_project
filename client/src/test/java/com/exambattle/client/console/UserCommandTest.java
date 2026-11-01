package com.exambattle.client.console;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class UserCommandTest {

    @Test
    void plainTextIsChat() {
        assertEquals(new UserCommand.Chat("大家好"), UserCommand.parse("  大家好 "));
    }

    @Test
    void registerTakesUsernameAndOptionalDisplayName() {
        assertEquals(new UserCommand.Register("alice", "Alice 王"), UserCommand.parse("/register alice Alice 王"));
        assertEquals(new UserCommand.Register("bob", "bob"), UserCommand.parse("/register bob"));
        assertInstanceOf(UserCommand.Usage.class, UserCommand.parse("/register"));
    }

    @Test
    void loginAndRoomCommands() {
        assertEquals(new UserCommand.Login("alice"), UserCommand.parse("/LOGIN alice"));
        assertInstanceOf(UserCommand.Usage.class, UserCommand.parse("/login"));
        assertEquals(new UserCommand.CreateRoom(), UserCommand.parse("/create"));
        assertEquals(new UserCommand.JoinRoom("k7m2xq"), UserCommand.parse("/join k7m2xq"));
        assertInstanceOf(UserCommand.Usage.class, UserCommand.parse("/join"));
        assertEquals(new UserCommand.LeaveRoom(), UserCommand.parse("/leave"));
        assertEquals(new UserCommand.ShowRoom(), UserCommand.parse("/room"));
    }

    @Test
    void basicCommands() {
        assertEquals(new UserCommand.Ping(), UserCommand.parse("/PING"));
        assertEquals(new UserCommand.Help(), UserCommand.parse("/help"));
        assertEquals(new UserCommand.Quit(), UserCommand.parse("/quit"));
        assertEquals(new UserCommand.Quit(), UserCommand.parse("/exit"));
    }

    @Test
    void doubleSlashSendsSlashText() {
        assertEquals(new UserCommand.Chat("/ping is a command"), UserCommand.parse("//ping is a command"));
    }

    @Test
    void unknownCommandAndEmptyLine() {
        assertEquals(new UserCommand.Unknown("/dance"), UserCommand.parse("/dance now"));
        assertEquals(new UserCommand.Empty(), UserCommand.parse("   "));
    }

    @Test
    void endOfInputMeansQuit() {
        assertEquals(new UserCommand.Quit(), UserCommand.parse(null));
    }
}
