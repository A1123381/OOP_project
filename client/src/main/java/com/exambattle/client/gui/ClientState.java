package com.exambattle.client.gui;

import com.exambattle.client.text.MessageFormatter;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.ProtocolException;
import com.exambattle.common.protocol.payload.ChatBroadcast;
import com.exambattle.common.protocol.payload.ClientLeftPayload;
import com.exambattle.common.protocol.payload.LoginOk;
import com.exambattle.common.protocol.payload.RoomPlayerJoined;
import com.exambattle.common.protocol.payload.RoomPlayerLeft;
import com.exambattle.common.protocol.payload.RoomState;
import com.exambattle.common.protocol.payload.UserInfo;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * GUI 顯示用的狀態：登入者、在線名單、所在房間、聊天紀錄。
 * 只依伺服器送來的訊息更新，客戶端不自行判斷（計時、計分等一律以伺服器為準）。
 * {@link #apply} 必須在 JavaFX 執行緒呼叫；本類別不啟動 JavaFX，可直接單元測試。
 */
public final class ClientState {

    /** 每個聊天列表最多保留的行數。 */
    static final int MAX_CHAT_LINES = 500;

    private final MessageFormatter formatter;
    private final ObjectProperty<UserInfo> me = new SimpleObjectProperty<>();
    /** 是否為管理員（第八階段）：可以下架別人的公開考卷。 */
    private final javafx.beans.property.BooleanProperty admin = new javafx.beans.property.SimpleBooleanProperty();
    private final ObjectProperty<RoomState> room = new SimpleObjectProperty<>();
    /** 最近一則競賽訊息（GAME_STARTED、GAME_QUESTION、GAME_PROGRESS、QUESTION_RESULT、GAME_OVER），給房間畫面使用。 */
    private final ObjectProperty<Message> game = new SimpleObjectProperty<>();
    private final ObservableList<UserInfo> online = FXCollections.observableArrayList();
    private final ObservableList<String> lobbyChat = FXCollections.observableArrayList();
    private final ObservableList<String> roomChat = FXCollections.observableArrayList();

    public ClientState() {
        this(ZoneId.systemDefault());
    }

    ClientState(ZoneId zone) {
        this.formatter = new MessageFormatter(zone);
    }

    public ObjectProperty<UserInfo> meProperty() {
        return me;
    }

    public UserInfo me() {
        return me.get();
    }

    public boolean admin() {
        return admin.get();
    }

    public javafx.beans.property.BooleanProperty adminProperty() {
        return admin;
    }

    public ObjectProperty<Message> gameProperty() {
        return game;
    }

    public ObjectProperty<RoomState> roomProperty() {
        return room;
    }

    public ObservableList<UserInfo> online() {
        return online;
    }

    public ObservableList<String> lobbyChat() {
        return lobbyChat;
    }

    public ObservableList<String> roomChat() {
        return roomChat;
    }

    /** 依伺服器訊息更新狀態。 */
    public void apply(Message message) {
        MessageType type = MessageType.fromWire(message.type()).orElse(null);
        if (type == null) {
            return;
        }
        try {
            switch (type) {
                case LOGIN_OK -> {
                    LoginOk ok = message.payloadAs(LoginOk.class);
                    me.set(ok.user());
                    admin.set(ok.admin());
                    online.setAll(ok.online());
                    // 主控台的歡迎詞含指令說明，GUI 使用自己的文字
                    add(lobbyChat, "* 登入成功，歡迎 " + ok.user().displayName() + "！可以在右側建立或加入房間，或到「題庫」分頁管理考卷。");
                }
                case CLIENT_JOINED -> {
                    online.add(message.payloadAs(UserInfo.class));
                    describe(message).ifPresent(line -> add(lobbyChat, line));
                }
                case CLIENT_LEFT -> {
                    long userId = message.payloadAs(ClientLeftPayload.class).user().userId();
                    online.removeIf(u -> u.userId() == userId);
                    describe(message).ifPresent(line -> add(lobbyChat, line));
                }
                case ROOM_STATE -> {
                    RoomState state = message.payloadAs(RoomState.class);
                    RoomState previous = room.get();
                    if (previous == null || !previous.roomCode().equals(state.roomCode())) {
                        roomChat.clear();
                        add(roomChat, "* 你在房間 " + state.roomCode());
                    }
                    room.set(state);
                }
                case ROOM_PLAYER_JOINED -> {
                    RoomPlayerJoined joined = message.payloadAs(RoomPlayerJoined.class);
                    updateRoomPlayers(players -> players.add(joined.player()));
                    describe(message).ifPresent(line -> add(roomChat, line));
                }
                case ROOM_PLAYER_LEFT -> {
                    long userId = message.payloadAs(RoomPlayerLeft.class).player().userId();
                    updateRoomPlayers(players -> players.removeIf(p -> p.userId() == userId));
                    describe(message).ifPresent(line -> add(roomChat, line));
                }
                case GAME_STARTED, GAME_QUESTION, GAME_PROGRESS, QUESTION_RESULT, GAME_OVER -> {
                    if (type == MessageType.GAME_STARTED || type == MessageType.GAME_OVER) {
                        setPlaying(type == MessageType.GAME_STARTED);
                        describe(message).ifPresent(line -> add(roomChat, line));
                    }
                    game.set(message);
                }
                case KICKED -> {
                    room.set(null);
                    describe(message).ifPresent(line -> add(lobbyChat, line));
                }
                case ROOM_CLOSED, LEFT_ROOM -> {
                    room.set(null);
                    describe(message).ifPresent(line -> {
                        add(roomChat, line);
                        add(lobbyChat, line);
                    });
                }
                case CHAT -> {
                    ChatBroadcast chat = message.payloadAs(ChatBroadcast.class);
                    add(chat.roomCode() == null ? lobbyChat : roomChat, formatter.chat(chat, me.get()));
                }
                default -> {
                    // 其他訊息（題庫回應、錯誤）由發出請求的畫面處理
                }
            }
        } catch (ProtocolException e) {
            add(lobbyChat, "? 伺服器訊息格式錯誤（" + message.type() + "）");
        }
    }

    private Optional<String> describe(Message message) throws ProtocolException {
        return formatter.format(message, me.get());
    }

    private void updateRoomPlayers(java.util.function.Consumer<List<UserInfo>> change) {
        RoomState current = room.get();
        if (current == null) {
            return;
        }
        List<UserInfo> players = new ArrayList<>(current.players());
        change.accept(players);
        room.set(new RoomState(current.roomCode(), current.ownerUserId(), current.maxPlayers(), List.copyOf(players),
                current.playing()));
    }

    private void setPlaying(boolean playing) {
        RoomState current = room.get();
        if (current != null) {
            room.set(new RoomState(current.roomCode(), current.ownerUserId(), current.maxPlayers(), current.players(),
                    playing));
        }
    }

    private static void add(ObservableList<String> list, String line) {
        list.add(line);
        if (list.size() > MAX_CHAT_LINES) {
            list.remove(0, list.size() - MAX_CHAT_LINES);
        }
    }
}
