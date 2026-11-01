package com.exambattle.client.gui;

import javafx.geometry.Insets;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 題組文章（閱讀測驗、克漏字）：可收合、可捲動，克漏字的空格【11】以粗體顯示（見 DECISIONS.md D-034）。
 * 練習、結果與錯題本共用。
 */
final class PassageView {

    private static final Pattern BLANK = Pattern.compile("【\\s*\\d{1,3}\\s*】");
    private static final double MAX_HEIGHT = 260;

    private PassageView() {
    }

    /** @param expanded 初始是否展開 */
    static TitledPane create(String passage, double fontSize, boolean expanded) {
        TextFlow flow = flow(passage, fontSize);
        ScrollPane scroll = new ScrollPane(flow);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(MAX_HEIGHT, 40 + passage.length() / 3.0));
        scroll.setMaxHeight(MAX_HEIGHT);
        TitledPane pane = new TitledPane("題組文章", scroll);
        pane.setExpanded(expanded);
        pane.setAnimated(false);
        return pane;
    }

    /** 文章轉成文字節點；【N】加粗。 */
    static TextFlow flow(String passage, double fontSize) {
        TextFlow flow = new TextFlow();
        Matcher m = BLANK.matcher(passage);
        int last = 0;
        while (m.find()) {
            flow.getChildren().add(text(passage.substring(last, m.start()), fontSize, false));
            flow.getChildren().add(text(m.group(), fontSize, true));
            last = m.end();
        }
        flow.getChildren().add(text(passage.substring(last), fontSize, false));
        flow.setLineSpacing(4);
        flow.setPadding(new Insets(6, 10, 6, 10));
        return flow;
    }

    private static Text text(String s, double fontSize, boolean bold) {
        Text t = new Text(s);
        t.setFont(Font.font(null, bold ? FontWeight.BOLD : FontWeight.NORMAL, fontSize));
        if (bold) {
            t.setStyle("-fx-fill: #1565c0;");
        }
        return t;
    }
}
