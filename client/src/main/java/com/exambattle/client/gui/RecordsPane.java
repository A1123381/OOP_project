package com.exambattle.client.gui;

import com.exambattle.client.gui.model.ResultText;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.practice.PracticeResult;
import com.exambattle.common.protocol.practice.PracticeStats;
import com.exambattle.common.protocol.practice.RecordList;
import com.exambattle.common.protocol.practice.RecordRef;
import com.exambattle.common.protocol.practice.RecordSummary;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.ZoneId;
import java.util.function.Function;

/** 「紀錄」分頁：個人答題統計與練習紀錄；雙擊紀錄開啟當次的詳細結果。 */
final class RecordsPane {

    private final GuiSession session;
    private final VBox root = new VBox(12);
    private final HBox stats = new HBox(28);
    private final TableView<RecordSummary> table = new TableView<>();

    RecordsPane(GuiSession session) {
        this.session = session;
        Label heading = new Label("個人成績");
        heading.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        ZoneId zone = ZoneId.systemDefault();
        table.getColumns().add(column("完成時間", 140, r -> ResultText.dateTime(r.finishedAt(), zone)));
        table.getColumns().add(column("方式", 150, r -> ResultText.mode(r.mode()) + "／" + ResultText.reveal(r.revealMode())));
        table.getColumns().add(column("名稱", 220, RecordSummary::title));
        table.getColumns().add(column("分數", 60, r -> ResultText.score(r.score())));
        table.getColumns().add(column("答對／題數", 90, r -> r.correctCount() + " / " + r.questionCount()));
        table.getColumns().add(column("正確率", 70,
                r -> r.questionCount() == 0 ? "-" : ResultText.percent((double) r.correctCount() / r.questionCount())));
        table.getColumns().add(column("作答時間", 100, r -> ResultText.duration(r.durationMs())));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("還沒有練習紀錄，到「練習」分頁開始第一次練習吧"));
        table.setRowFactory(v -> {
            TableRow<RecordSummary> row = new TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    open(row.getItem());
                }
            });
            return row;
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        Button openButton = new Button("查看詳細結果");
        openButton.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        openButton.setOnAction(e -> open(table.getSelectionModel().getSelectedItem()));
        Button refresh = new Button("重新整理");
        refresh.setOnAction(e -> refresh());

        root.getChildren().addAll(heading, stats, new Label("練習紀錄（雙擊開啟詳細結果）"), table,
                new HBox(10, openButton, refresh));
        root.setPadding(new Insets(16));
    }

    Parent root() {
        return root;
    }

    void refresh() {
        session.call(MessageType.LIST_RECORDS, null, RecordList.class, list -> {
            table.getItems().setAll(list.records());
            PracticeStats s = list.stats();
            stats.getChildren().setAll(
                    stat("練習次數", s.practiceCount() + " 次"),
                    stat("作答題數", s.questionCount() + " 題"),
                    stat("答對題數", s.correctCount() + " 題"),
                    stat("正確率", s.questionCount() == 0 ? "-" : ResultText.percent(s.accuracy())),
                    stat("平均分數", s.practiceCount() == 0 ? "-" : ResultText.score(s.averageScore())));
        });
    }

    private void open(RecordSummary record) {
        session.call(MessageType.GET_RECORD, new RecordRef(record.id()), PracticeResult.class, result -> {
            Stage stage = new Stage();
            stage.setTitle("練習結果 - " + record.title());
            Parent content = new ResultView(result).root();
            content.setStyle(root.getScene().getRoot().getStyle());
            stage.setScene(new Scene(content, 820, 680));
            stage.initOwner(root.getScene().getWindow());
            stage.show();
        });
    }

    private static VBox stat(String name, String value) {
        Label n = new Label(name);
        n.setStyle("-fx-text-fill: #555;");
        Label v = new Label(value);
        v.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        return new VBox(2, n, v);
    }

    private static TableColumn<RecordSummary, String> column(String title, double width,
                                                             Function<RecordSummary, String> value) {
        TableColumn<RecordSummary, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        c.setPrefWidth(width);
        return c;
    }
}
