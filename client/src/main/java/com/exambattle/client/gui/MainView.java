package com.exambattle.client.gui;

import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.payload.ChatRequest;
import com.exambattle.common.protocol.payload.JoinRoomRequest;
import com.exambattle.common.protocol.payload.RoomState;
import com.exambattle.common.protocol.payload.UserInfo;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
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

/** 主視窗：上方顯示登入者與位置，分頁為大廳、房間（競賽）、題庫、練習、紀錄、錯題本、匯入。 */
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
        Label adminBadge = new Label("【管理員】");
        adminBadge.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
        adminBadge.visibleProperty().bind(state.adminProperty());
        adminBadge.managedProperty().bind(adminBadge.visibleProperty());
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label keys = new Label("Ctrl+1–7 切換分頁　Ctrl+＋／－ 調整字級　Ctrl+0 還原");
        keys.setStyle("-fx-text-fill: #667;");
        HBox top = new HBox(10, who, adminBadge, spacer, keys);
        top.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        top.setPadding(new Insets(10, 14, 10, 14));
        top.setStyle("-fx-background-color: #eef2f7;");

        Tab lobbyTab = new Tab("大廳", lobby());
        // 房間與多人競賽（第四階段）
        roomTab.setContent(new RoomPane(session, chatBox(state.roomChat())).root());
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
        // 快速鍵：Ctrl+1–7 切換分頁（第八階段）
        for (int i = 0; i < tabs.getTabs().size(); i++) {
            tabs.getTabs().get(i).setTooltip(new javafx.scene.control.Tooltip("Ctrl+" + (i + 1)));
        }
        root.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> {
            javafx.scene.input.KeyCode code = e.getCode();
            if (e.isShortcutDown() && code.compareTo(javafx.scene.input.KeyCode.DIGIT1) >= 0
                    && code.compareTo(javafx.scene.input.KeyCode.DIGIT9) <= 0) {
                int index = code.ordinal() - javafx.scene.input.KeyCode.DIGIT1.ordinal();
                if (index >= 0 && index < tabs.getTabs().size() && !tabs.getTabs().get(index).isDisabled()) {
                    tabs.getSelectionModel().select(index);
                    e.consume();
                }
            }
        });
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
