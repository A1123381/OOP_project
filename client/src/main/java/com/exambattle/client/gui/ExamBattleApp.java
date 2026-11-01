package com.exambattle.client.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** JavaFX 客戶端：登入畫面 → 主視窗；連線中斷時回到登入畫面。 */
public final class ExamBattleApp extends Application {

    /** 中文字型優先使用微軟正黑體。 */
    private static final String BASE_STYLE =
            "-fx-font-family: 'Microsoft JhengHei UI', 'Microsoft JhengHei', 'Segoe UI', sans-serif; -fx-font-size: 14px;";

    private Stage stage;
    private GuiSession session;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        stage.setTitle("Exam Battle");
        stage.setOnCloseRequest(e -> {
            if (session != null) {
                session.close();
            }
            Platform.exit();
        });
        showLogin(null);
        stage.show();
    }

    private void showLogin(String message) {
        session = null;
        LoginView login = new LoginView(this::showMain);
        setRoot(login.root(), 460, 420);
        if (message != null) {
            Dialogs.error(message);
        }
    }

    private void showMain(GuiSession loggedIn) {
        this.session = loggedIn;
        loggedIn.setOnDisconnected(reason -> showLogin("與伺服器的連線中斷：" + reason));
        MainView main = new MainView(loggedIn);
        setRoot(main.root(), 1180, 760);
        stage.centerOnScreen();
    }

    private void setRoot(Parent root, double width, double height) {
        root.setStyle(BASE_STYLE);
        stage.setScene(new Scene(root, width, height));
    }
}
