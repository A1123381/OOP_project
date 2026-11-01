package com.exambattle.client.gui;

import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.payload.ChatRequest;
import com.exambattle.common.protocol.payload.JoinRoomRequest;
import com.exambattle.common.protocol.payload.LeftRoom;
import com.exambattle.common.protocol.payload.RoomState;
import com.exambattle.common.protocol.payload.UserInfo;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** 主視窗：上方顯示登入者與位置，分頁為大廳、房間、題庫。 */
final class MainView {

    private final GuiSession session;
    private final ClientState state;
    private final BorderPane root = new BorderPane();
    private final TabPane tabs = new TabPane();
    private final Tab roomTab = new Tab("房間");

    MainView(GuiSession session) {
        this.session = session;
        this.state = session.state();

        Label who = new Label();
        who.textProperty().bind(Bindings.createStringBinding(() -> {
            UserInfo me = state.me();
            RoomState room = state.roomProperty().get();
            String where = room == null ? "大廳" : "房間 " + room.roomCode();
            return me == null ? "" : me.displayName() + "（" + me.username() + "）　目前位置：" + where;
        }, state.meProperty(), state.roomProperty()));
        who.setStyle("-fx-font-weight: bold;");
        HBox top = new HBox(who);
        top.setPadding(new Insets(10, 14, 10, 14));
        top.setStyle("-fx-background-color: #eef2f7;");

        Tab lobbyTab = new Tab("大廳", lobby());
        roomTab.setContent(room());
        Tab bankTab = new Tab("題庫", new BankPane(session).root());

        // 單人練習、紀錄、錯題本（第五階段）；切換到分頁時重新載入資料
        RecordsPane records = new RecordsPane(session);
        Tab practiceTab = new Tab("練習");
        Tab recordsTab = new Tab("紀錄", records.root());
        Tab wrongTab = new Tab("錯題本");
        PracticePane practice = new PracticePane(session, records::refresh);
        WrongBookPane wrongBook = new WrongBookPane(session, filter -> {
            practice.prepareWrongReview(filter);
            tabs.getSelectionModel().select(practiceTab);
        });
        practiceTab.setContent(practice.root());
        wrongTab.setContent(wrongBook.root());
        practiceTab.setOnSelectionChanged(e -> {
            if (practiceTab.isSelected()) {
                practice.refreshSources();
            }
        });
        recordsTab.setOnSelectionChanged(e -> {
            if (recordsTab.isSelected()) {
                records.refresh();
            }
        });
        wrongTab.setOnSelectionChanged(e -> {
            if (wrongTab.isSelected()) {
                wrongBook.refresh();
            }
        });
        // 考卷匯入（第六階段）：只在本機處理，不經過伺服器
        Tab importTab = new Tab("匯入", new ImportPane(session).root());
        tabs.getTabs().addAll(lobbyTab, roomTab, bankTab, practiceTab, recordsTab, wrongTab, importTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        roomTab.disableProperty().bind(state.roomProperty().isNull());
        state.roomProperty().addListener((obs, before, after) -> {
            if (before == null && after != null) {
                tabs.getSelectionModel().select(roomTab);
            } else if (after == null && tabs.getSelectionModel().getSelectedItem() == roomTab) {
                tabs.getSelectionModel().select(lobbyTab);
            }
        });

        root.setTop(top);
        root.setCenter(tabs);
    }

    Parent root() {
        return root;
    }

    private Parent lobby() {
        ListView<UserInfo> online = new ListView<>(state.online());
        online.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(UserInfo user, boolean empty) {
                super.updateItem(user, empty);
                setText(empty || user == null ? null : user.displayName() + "（" + user.username() + "）");
            }
        });
        online.setPrefWidth(220);
        VBox left = titled("其他在線使用者", online);

        TextField code = new TextField();
        code.setPromptText("房間代碼");
        Button join = new Button("加入房間");
        join.setOnAction(e -> {
            if (!code.getText().isBlank()) {
                session.call(MessageType.JOIN_ROOM, new JoinRoomRequest(code.getText()), RoomState.class, r -> code.clear());
            }
        });
        code.setOnAction(join.getOnAction());
        Button create = new Button("建立房間");
        create.setMaxWidth(Double.MAX_VALUE);
        create.setOnAction(e -> session.call(MessageType.CREATE_ROOM, null, RoomState.class, r -> { }));
        VBox right = new VBox(10, new Label("競賽房間"), create, new Label("或輸入代碼加入"), code, join);
        right.setPadding(new Insets(12));
        right.setPrefWidth(200);
        join.disableProperty().bind(state.roomProperty().isNotNull());
        create.disableProperty().bind(state.roomProperty().isNotNull());

        BorderPane pane = new BorderPane();
        pane.setLeft(left);
        pane.setCenter(titled("大廳聊天（只有大廳的人看得到）", chatBox(state.lobbyChat())));
        pane.setRight(right);
        return pane;
    }

    private Parent room() {
        Label header = new Label();
        header.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        header.textProperty().bind(Bindings.createStringBinding(() -> {
            RoomState r = state.roomProperty().get();
            return r == null ? "尚未進入房間"
                    : "房間代碼 " + r.roomCode() + "（" + r.players().size() + "/" + r.maxPlayers() + " 人）";
        }, state.roomProperty()));

        ListView<UserInfo> players = new ListView<>();
        state.roomProperty().addListener((obs, before, after) ->
                players.getItems().setAll(after == null ? java.util.List.of() : after.players()));
        players.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(UserInfo user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setText(null);
                    return;
                }
                RoomState r = state.roomProperty().get();
                String text = user.displayName() + "（" + user.username() + "）";
                if (r != null && r.ownerUserId() == user.userId()) {
                    text += "【房主】";
                }
                if (state.me() != null && state.me().userId() == user.userId()) {
                    text += "（你）";
                }
                setText(text);
            }
        });
        players.setPrefWidth(300);

        Button leave = new Button("離開房間");
        leave.setOnAction(e -> {
            RoomState r = state.roomProperty().get();
            boolean owner = r != null && state.me() != null && r.ownerUserId() == state.me().userId();
            if (!owner || Dialogs.confirm("你是房主，離開後房間會解散。確定要離開嗎？")) {
                session.call(MessageType.LEAVE_ROOM, null, LeftRoom.class, left -> { });
            }
        });
        Label hint = new Label("房主離開或斷線時房間會解散。競賽功能將於第四階段加入。");
        hint.setStyle("-fx-text-fill: #555;");
        HBox top = new HBox(16, header, leave, hint);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10));

        BorderPane pane = new BorderPane();
        pane.setTop(top);
        pane.setLeft(titled("房間玩家", players));
        pane.setCenter(titled("房間聊天（只有同房玩家看得到）", chatBox(state.roomChat())));
        return pane;
    }

    /** 聊天列表與輸入框；送出的訊息由伺服器廣播回來後才顯示。 */
    private Parent chatBox(ObservableList<String> lines) {
        ListView<String> list = new ListView<>(lines);
        lines.addListener((ListChangeListener<String>) c -> list.scrollTo(lines.size() - 1));
        VBox.setVgrow(list, Priority.ALWAYS);
        TextField input = new TextField();
        input.setPromptText("輸入訊息後按 Enter");
        Button send = new Button("送出");
        Runnable doSend = () -> {
            String text = input.getText();
            if (!text.isBlank()) {
                session.call(MessageType.CHAT, new ChatRequest(text), Void.class, ok -> input.clear());
            }
        };
        send.setOnAction(e -> doSend.run());
        input.setOnAction(e -> doSend.run());
        HBox.setHgrow(input, Priority.ALWAYS);
        return new VBox(8, list, new HBox(8, input, send));
    }

    static VBox titled(String title, Parent content) {
        Label label = new Label(title);
        label.setStyle("-fx-font-weight: bold;");
        VBox box = new VBox(8, label, content);
        box.setPadding(new Insets(12));
        if (content instanceof javafx.scene.layout.Region region) {
            VBox.setVgrow(region, Priority.ALWAYS);
        }
        return box;
    }
}
