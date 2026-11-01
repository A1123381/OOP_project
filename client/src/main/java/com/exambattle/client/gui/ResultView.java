package com.exambattle.client.gui;

import com.exambattle.client.gui.model.QuestionTypes;
import com.exambattle.client.gui.model.ResultText;
import com.exambattle.common.protocol.bank.OptionData;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.practice.AnswerData;
import com.exambattle.common.protocol.practice.PracticeResult;
import com.exambattle.common.protocol.practice.RecordSummary;
import com.exambattle.common.protocol.practice.ResultItem;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.util.List;

/**
 * 練習結果：總分、答對／答錯題數、正確率、作答時間，以及每題的你的答案、正確答案、得分與解說。
 * 交卷後與在「紀錄」分頁開啟歷史紀錄時共用。題目為作答當時的快照。
 */
final class ResultView {

    private static final String WRONG_COLOR = "#c62828";
    private static final String RIGHT_COLOR = "#2e7d32";

    private final BorderPane root = new BorderPane();

    /** @param actions 顯示在上方的按鈕（例如再練一次、回到設定），可為空 */
    ResultView(PracticeResult result, Node... actions) {
        RecordSummary r = result.record();
        Label title = new Label(r.title());
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        title.setWrapText(true);
        Label sub = new Label(ResultText.mode(r.mode()) + "・" + ResultText.reveal(r.revealMode()) + "・"
                + ResultText.dateTime(r.finishedAt(), ZoneId.systemDefault()));
        sub.setStyle("-fx-text-fill: #555;");

        Label score = new Label(ResultText.score(r.score()) + " 分");
        score.setStyle("-fx-font-size: 32px; -fx-font-weight: bold; -fx-text-fill: #1565c0;");
        int wrong = r.questionCount() - r.correctCount();
        GridPane stats = new GridPane();
        stats.setHgap(24);
        stats.setVgap(4);
        stats.addRow(0, stat("答對", r.correctCount() + " 題"), stat("答錯", wrong + " 題"),
                stat("正確率", r.questionCount() == 0 ? "-" : ResultText.percent((double) r.correctCount() / r.questionCount())),
                stat("作答時間", ResultText.duration(r.durationMs())));
        HBox summary = new HBox(28, score, stats);
        summary.setStyle("-fx-alignment: center-left;");

        HBox buttons = new HBox(10, actions);
        VBox top = new VBox(8, title, sub, summary, buttons);
        top.setPadding(new Insets(14));
        top.setStyle("-fx-background-color: #f5f8fc;");

        VBox items = new VBox(12);
        items.setPadding(new Insets(14));
        if (wrong > 0) {
            Label hint = new Label("答錯的 " + wrong + " 題（含部分給分與未作答）已加入錯題本。");
            hint.setStyle("-fx-text-fill: " + WRONG_COLOR + ";");
            items.getChildren().add(hint);
        }
        for (ResultItem item : result.questions()) {
            items.getChildren().add(itemBox(item));
        }
        ScrollPane scroll = new ScrollPane(items);
        scroll.setFitToWidth(true);

        root.setTop(top);
        root.setCenter(scroll);
    }

    Parent root() {
        return root;
    }

    private static VBox stat(String name, String value) {
        Label n = new Label(name);
        n.setStyle("-fx-text-fill: #555;");
        Label v = new Label(value);
        v.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        return new VBox(2, n, v);
    }

    private static VBox itemBox(ResultItem item) {
        QuestionData q = item.question();
        AnswerData a = item.answer();
        String verdict = item.correct() ? "✔ 答對" : item.score() > 0 ? "△ 部分給分" : "✘ 答錯";
        Label header = new Label("第 " + (item.index() + 1) + " 題［" + QuestionTypes.label(q.type()) + "］　"
                + verdict + "　得分 " + ResultText.questionScore(item.score()));
        header.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (item.correct() ? RIGHT_COLOR : WRONG_COLOR) + ";");
        Label content = new Label(q.content());
        content.setWrapText(true);

        VBox box = new VBox(6, header, content);
        if (q.type() != QuestionType.TRUE_FALSE) {
            List<Long> picked = a == null || a.selectedOptionIds() == null ? List.of() : a.selectedOptionIds();
            for (int i = 0; i < q.options().size(); i++) {
                OptionData o = q.options().get(i);
                boolean correct = Boolean.TRUE.equals(o.correct());
                boolean chosen = picked.contains(o.id());
                Label line = new Label((correct ? "✔ " : "　 ") + (chosen ? "● " : "○ ")
                        + ResultText.letter(i) + ". " + o.content());
                line.setWrapText(true);
                if (chosen && !correct) {
                    line.setStyle("-fx-text-fill: " + WRONG_COLOR + ";");
                } else if (correct) {
                    line.setStyle("-fx-text-fill: " + RIGHT_COLOR + ";");
                }
                box.getChildren().add(line);
            }
        }
        Label answers = new Label("你的答案：" + ResultText.yourAnswer(q, a) + "　　正確答案：" + ResultText.correctAnswer(q));
        box.getChildren().add(answers);
        if (q.explanation() != null && !q.explanation().isBlank()) {
            Label explanation = new Label("解說：" + q.explanation());
            explanation.setWrapText(true);
            explanation.setStyle("-fx-text-fill: #444;");
            box.getChildren().add(explanation);
        }
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: white; -fx-border-color: "
                + (item.correct() ? "#c8e6c9" : "#ffcdd2") + "; -fx-border-radius: 4; -fx-background-radius: 4;");
        return box;
    }
}
