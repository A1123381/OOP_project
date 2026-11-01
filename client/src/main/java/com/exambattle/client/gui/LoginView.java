package com.exambattle.client.gui;

import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.payload.LoginOk;
import com.exambattle.common.protocol.payload.LoginRequest;
import com.exambattle.common.protocol.payload.RegisterRequest;
import com.exambattle.common.protocol.payload.UserInfo;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.function.Consumer;

/** 連線與登入畫面。連線在背景執行緒建立，避免卡住畫面。 */
final class LoginView {

    private final TextField host = new TextField("localhost");
    private final TextField port = new TextField(String.valueOf(Protocol.DEFAULT_PORT));
    private final TextField username = new TextField();
    private final PasswordField password = new PasswordField();
    private final Label status = new Label();
    private final Button loginButton = new Button("登入");
    private final Button registerButton = new Button("註冊新帳號…");
    private final Consumer<GuiSession> onLoggedIn;
    private final VBox root;

    /** 已建立的連線；連線中斷或主機變更時重建。 */
    private GuiSession session;
    private String sessionTarget;

    LoginView(Consumer<GuiSession> onLoggedIn) {
        this.onLoggedIn = onLoggedIn;

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("伺服器"), host);
        form.addRow(1, new Label("埠號"), port);
        form.addRow(2, new Label("帳號"), username);
        form.addRow(3, new Label("密碼"), password);

        loginButton.setDefaultButton(true);
        loginButton.setOnAction(e -> login());
        registerButton.setOnAction(e -> register());
        HBox buttons = new HBox(10, loginButton, registerButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        Label title = new Label("Exam Battle");
        title.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");
        Label subtitle = new Label("考試題庫與多人競賽練習");
        status.setWrapText(true);
        status.setStyle("-fx-text-fill: #b00020;");

        root = new VBox(14, title, subtitle, form, buttons, status);
        root.setPadding(new Insets(28));
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPrefWidth(420);
    }

    Parent root() {
        return root;
    }

    private void login() {
        String user = username.getText().strip();
        String pw = password.getText();
        if (user.isEmpty() || pw.isEmpty()) {
            status.setText("請輸入帳號與密碼");
            return;
        }
        withSession(s -> s.call(MessageType.LOGIN, new LoginRequest(user, pw), LoginOk.class,
                ok -> {
                    password.clear();
                    onLoggedIn.accept(s);
                },
                this::fail));
    }

    private void register() {
        Dialog<RegisterRequest> dialog = new Dialog<>();
        dialog.setTitle("註冊新帳號");
        dialog.setHeaderText("帳號 3–20 個英數字或底線；密碼 " + Protocol.MIN_PASSWORD_CHARS + "–"
                + Protocol.MAX_PASSWORD_CHARS + " 字元");
        TextField user = new TextField(username.getText());
        TextField display = new TextField();
        PasswordField pw = new PasswordField();
        PasswordField confirm = new PasswordField();
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("帳號"), user);
        form.addRow(1, new Label("顯示名稱"), display);
        form.addRow(2, new Label("密碼"), pw);
        form.addRow(3, new Label("確認密碼"), confirm);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (!pw.getText().equals(confirm.getText())) {
                Dialogs.error("兩次輸入的密碼不一致");
                e.consume();
            }
        });
        dialog.setResultConverter(b -> b == ButtonType.OK
                ? new RegisterRequest(user.getText().strip(), pw.getText(),
                        display.getText().isBlank() ? user.getText().strip() : display.getText().strip())
                : null);
        dialog.showAndWait().ifPresent(request -> withSession(s -> s.call(MessageType.REGISTER, request, UserInfo.class,
                created -> {
                    username.setText(created.username());
                    password.clear();
                    status.setText("");
                    Dialogs.info("註冊成功：" + created.displayName() + "（" + created.username() + "）\n請輸入密碼登入。");
                },
                this::fail)));
    }

    /** 確保已連線後執行 action（在 JavaFX 執行緒）。 */
    private void withSession(Consumer<GuiSession> action) {
        String target = host.getText().strip() + ":" + port.getText().strip();
        if (session != null && target.equals(sessionTarget)) {
            action.accept(session);
            return;
        }
        int portNumber;
        try {
            portNumber = Integer.parseInt(port.getText().strip());
        } catch (NumberFormatException e) {
            status.setText("埠號必須是數字");
            return;
        }
        String hostName = host.getText().strip();
        setBusy(true, "連線中…");
        Thread.ofVirtual().start(() -> {
            try {
                GuiSession opened = GuiSession.open(hostName, portNumber, new ClientState());
                javafx.application.Platform.runLater(() -> {
                    if (session != null) {
                        session.close();
                    }
                    session = opened;
                    sessionTarget = target;
                    opened.setOnDisconnected(reason -> {
                        session = null;
                        status.setText("連線中斷：" + reason);
                    });
                    setBusy(false, "");
                    action.accept(opened);
                });
            } catch (IOException e) {
                javafx.application.Platform.runLater(() ->
                        setBusy(false, "無法連線到 " + target + "：" + e.getMessage() + "\n請確認伺服器已啟動。"));
            }
        });
    }

    private void fail(String message) {
        status.setText(message);
    }

    private void setBusy(boolean busy, String text) {
        loginButton.setDisable(busy);
        registerButton.setDisable(busy);
        status.setText(text);
    }
}
