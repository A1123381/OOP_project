package com.exambattle.client.console;

/** 使用者在主控台輸入的一行，解析後的指令。 */
public sealed interface UserCommand {

    record Chat(String text) implements UserCommand {
    }

    /** displayName 省略時以帳號代替。 */
    record Register(String username, String displayName) implements UserCommand {
    }

    record Login(String username) implements UserCommand {
    }

    record CreateRoom() implements UserCommand {
    }

    record JoinRoom(String roomCode) implements UserCommand {
    }

    record LeaveRoom() implements UserCommand {
    }

    record ShowRoom() implements UserCommand {
    }

    record Ping() implements UserCommand {
    }

    record Help() implements UserCommand {
    }

    record Quit() implements UserCommand {
    }

    /** 指令格式錯誤，hint 為正確用法。 */
    record Usage(String hint) implements UserCommand {
    }

    record Unknown(String command) implements UserCommand {
    }

    record Empty() implements UserCommand {
    }

    /**
     * 以 / 開頭的是指令，其他文字視為聊天訊息。
     * 想送出以 / 開頭的聊天，可輸入 // 開頭。
     */
    static UserCommand parse(String line) {
        if (line == null) {
            return new Quit();
        }
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            return new Empty();
        }
        if (trimmed.startsWith("//")) {
            return new Chat(trimmed.substring(1));
        }
        if (!trimmed.startsWith("/")) {
            return new Chat(trimmed);
        }
        String[] parts = trimmed.split("\\s+", 3);
        String command = parts[0].toLowerCase();
        String arg1 = parts.length > 1 ? parts[1] : null;
        String rest = parts.length > 2 ? parts[2] : null;
        return switch (command) {
            case "/register" -> arg1 == null
                    ? new Usage("/register <帳號> [顯示名稱]")
                    : new Register(arg1, rest == null ? arg1 : rest);
            case "/login" -> arg1 == null ? new Usage("/login <帳號>") : new Login(arg1);
            case "/create" -> new CreateRoom();
            case "/join" -> arg1 == null ? new Usage("/join <房間代碼>") : new JoinRoom(arg1);
            case "/leave" -> new LeaveRoom();
            case "/room" -> new ShowRoom();
            case "/ping" -> new Ping();
            case "/help", "/?" -> new Help();
            case "/quit", "/exit" -> new Quit();
            default -> new Unknown(command);
        };
    }
}
