package com.exambattle.client.gui;

import javafx.application.Application;

/**
 * GUI 進入點。JavaFX 放在 classpath（而非 module path）時，main 類別不能繼承 Application，
 * 所以由這個類別啟動 {@link ExamBattleApp}。
 *
 * <pre>java -jar client/target/client-0.1.0-SNAPSHOT.jar</pre>
 */
public final class GuiLauncher {

    private GuiLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(ExamBattleApp.class, args);
    }
}
