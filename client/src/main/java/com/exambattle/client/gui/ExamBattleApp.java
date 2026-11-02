package com.exambattle.client.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;

import java.util.prefs.Preferences;

/**
 * JavaFX 客戶端：登入畫面 → 主視窗；連線中斷時回到登入畫面。
 * 共用外觀放在 app.css；字級可用 Ctrl + ＋／－／0 調整，並記在這台電腦上（第八階段）。
 */
public final class ExamBattleApp extends Application {

    /** 中文字型優先使用微軟正黑體。 */
    private static final String FONT_FAMILY = "-fx-font-family: 'Microsoft JhengHei UI', 'Microsoft JhengHei', 'Segoe UI', sans-serif;";
    private static final int DEFAULT_FONT_SIZE = 14;
    private static final int MIN_FONT_SIZE = 11;
    private static final int MAX_FONT_SIZE = 22;
    private static final String FONT_SIZE_KEY = "fontSize";

    private final Preferences prefs = Preferences.userNodeForPackage(ExamBattleApp.class);
    private Stage stage;
    private GuiSession session;
    private int fontSize;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        fontSize = Math.clamp(prefs.getInt(FONT_SIZE_KEY, DEFAULT_FONT_SIZE), MIN_FONT_SIZE, MAX_FONT_SIZE);
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
        stage.setTitle("Exam Battle");
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
        if (loggedIn.state().me() != null) {
            stage.setTitle("Exam Battle — " + loggedIn.state().me().displayName());
        }
        stage.centerOnScreen();
    }

    private void setRoot(Parent root, double width, double height) {
        applyFont(root);
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(ExamBattleApp.class.getResource("app.css").toExternalForm());
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!e.isShortcutDown()) {
                return;
            }
            int before = fontSize;
            if (e.getCode() == KeyCode.EQUALS || e.getCode() == KeyCode.PLUS || e.getCode() == KeyCode.ADD) {
                fontSize = Math.min(MAX_FONT_SIZE, fontSize + 1);
            } else if (e.getCode() == KeyCode.MINUS || e.getCode() == KeyCode.SUBTRACT) {
                fontSize = Math.max(MIN_FONT_SIZE, fontSize - 1);
            } else if (e.getCode() == KeyCode.DIGIT0 || e.getCode() == KeyCode.NUMPAD0) {
                fontSize = DEFAULT_FONT_SIZE;
            } else {
                return;
            }
            e.consume();
            if (fontSize != before) {
                prefs.putInt(FONT_SIZE_KEY, fontSize);
                applyFont(scene.getRoot());
            }
        });
        stage.setScene(scene);
    }

    private void applyFont(Parent root) {
        root.setStyle(FONT_FAMILY + " -fx-font-size: " + fontSize + "px;");
    }
}
