package com.exambattle.client.console;

import com.exambattle.client.net.ServerConnection;
import com.exambattle.client.text.MessageFormatter;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.ProtocolException;
import com.exambattle.common.protocol.payload.ChatRequest;
import com.exambattle.common.protocol.payload.HelloPayload;
import com.exambattle.common.protocol.payload.JoinRoomRequest;
import com.exambattle.common.protocol.payload.LoginOk;
import com.exambattle.common.protocol.payload.LoginRequest;
import com.exambattle.common.protocol.payload.RegisterRequest;
import com.exambattle.common.protocol.payload.RoomState;
import com.exambattle.common.protocol.payload.UserInfo;

import java.io.IOException;
import java.io.PrintStream;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 主控台介面：讀取使用者輸入轉成協定訊息，並顯示伺服器送來的訊息。
 * 登入狀態與所在房間只依伺服器的回覆更新；所有判斷以伺服器為準。
 */
public final class ConsoleClient implements ServerConnection.Listener {

    private static final String HELP = """
            指令：
              /register <帳號> [顯示名稱]   註冊（之後會詢問密碼）
              /login <帳號>                登入（之後會詢問密碼）
              /create                      建立房間
              /join <房間代碼>              加入房間
              /room                        顯示房間玩家
              /leave                       離開房間
              直接輸入文字                   聊天（在房間內只有同房看得到；以 / 開頭請輸入 //）
              /ping                        測試往返時間
              /help                        顯示說明
              /quit                        離開""";

    private final PrintStream out;
    private final MessageFormatter formatter = new MessageFormatter(ZoneId.systemDefault());
    private final Runnable onExit;
    private final Map<String, Long> pendingPings = new ConcurrentHashMap<>();

    private volatile UserInfo me;
    private volatile String roomCode;
    private volatile boolean disconnected;

    /**
     * @param onExit 連線被伺服器中斷時呼叫，用來結束程式（主執行緒可能正卡在讀取輸入）
     */
    public ConsoleClient(PrintStream out, Runnable onExit) {
        this.out = out;
        this.onExit = onExit;
    }

    /** 連線、送出 HELLO，然後處理使用者輸入直到 /quit 或輸入結束。回傳程式結束碼。 */
    public int run(String host, int port, UserInput input) {
        ServerConnection connection;
        try {
            connection = ServerConnection.connect(host, port, this);
        } catch (IOException e) {
            out.println("無法連線到 " + host + ":" + port + "：" + e.getMessage());
            out.println("請確認伺服器已啟動。");
            return 1;
        }
        try (connection) {
            connection.send(MessageType.HELLO, new HelloPayload(Protocol.VERSION));
            connection.startHeartbeat(Protocol.CLIENT_PING_INTERVAL);
            out.println(HELP);

            while (!disconnected) {
                if (input.interactive()) {
                    out.print(prompt());
                    out.flush();
                }
                String line = input.readLine();
                if (line == null || !handleInput(connection, input, UserCommand.parse(line))) {
                    break;
                }
            }
            if (!disconnected) {
                connection.sendByeAndClose();
                out.println("已離開。");
            }
            return 0;
        } catch (IOException e) {
            out.println("連線錯誤：" + e.getMessage());
            return 1;
        }
    }

    /** 提示列顯示目前位置。 */
    String prompt() {
        if (me == null) {
            return "[未登入] > ";
        }
        String code = roomCode;
        return code == null ? "[大廳] > " : "[房間 " + code + "] > ";
    }

    /** 回傳 false 表示要離開。 */
    private boolean handleInput(ServerConnection connection, UserInput input, UserCommand command) throws IOException {
        switch (command) {
            case UserCommand.Chat chat -> connection.send(MessageType.CHAT, new ChatRequest(chat.text()));
            case UserCommand.Register register -> {
                String password = input.readPassword("設定密碼（" + Protocol.MIN_PASSWORD_CHARS + "–"
                        + Protocol.MAX_PASSWORD_CHARS + " 字元）：");
                if (password != null) {
                    connection.send(MessageType.REGISTER,
                            new RegisterRequest(register.username(), password, register.displayName()));
                }
            }
            case UserCommand.Login login -> {
                String password = input.readPassword("密碼：");
                if (password != null) {
                    connection.send(MessageType.LOGIN, new LoginRequest(login.username(), password));
                }
            }
            case UserCommand.CreateRoom create -> connection.send(MessageType.CREATE_ROOM, null);
            case UserCommand.JoinRoom join -> connection.send(MessageType.JOIN_ROOM, new JoinRoomRequest(join.roomCode()));
            case UserCommand.LeaveRoom leave -> connection.send(MessageType.LEAVE_ROOM, null);
            case UserCommand.ShowRoom show -> connection.send(MessageType.GET_ROOM, null);
            case UserCommand.Ping ping -> pendingPings.put(connection.send(MessageType.PING, null), System.nanoTime());
            case UserCommand.Help help -> out.println(HELP);
            case UserCommand.Usage usage -> out.println("用法：" + usage.hint());
            case UserCommand.Unknown unknown -> out.println("不認得的指令 " + unknown.command() + "，輸入 /help 查看說明");
            case UserCommand.Empty empty -> {
                // 忽略空白行
            }
            case UserCommand.Quit quit -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onMessage(Message message) {
        if (message.is(MessageType.PONG)) {
            Long sentAt = pendingPings.remove(message.requestId());
            if (sentAt != null) {
                out.println("* PONG，往返 " + (System.nanoTime() - sentAt) / 1_000_000 + " ms");
            }
            return;
        }
        try {
            updateState(message);
            formatter.format(message, me).ifPresent(out::println);
        } catch (ProtocolException e) {
            out.println("? 伺服器訊息格式錯誤（" + message.type() + "）：" + e.getMessage());
        }
    }

    /** 依伺服器的回覆更新登入者與所在房間。 */
    private void updateState(Message message) throws ProtocolException {
        MessageType type = MessageType.fromWire(message.type()).orElse(null);
        if (type == null) {
            return;
        }
        switch (type) {
            case LOGIN_OK -> me = message.payloadAs(LoginOk.class).user();
            case ROOM_STATE -> roomCode = message.payloadAs(RoomState.class).roomCode();
            case LEFT_ROOM, ROOM_CLOSED -> roomCode = null;
            default -> {
                // 其他訊息不影響狀態
            }
        }
    }

    @Override
    public void onDisconnected(String reason) {
        disconnected = true;
        out.println("* " + reason);
        onExit.run();
    }

    @Override
    public void onBadMessage(String line, String reason) {
        out.println("? 無法解析伺服器訊息：" + reason);
    }
}
