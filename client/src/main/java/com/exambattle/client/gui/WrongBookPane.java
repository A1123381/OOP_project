package com.exambattle.client.gui;

import com.exambattle.client.gui.model.QuestionTypes;
import com.exambattle.client.gui.model.ResultText;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.bank.SubjectInfo;
import com.exambattle.common.protocol.bank.SubjectList;
import com.exambattle.common.protocol.practice.ListWrongRequest;
import com.exambattle.common.protocol.practice.WrongItem;
import com.exambattle.common.protocol.practice.WrongList;
import com.exambattle.common.protocol.practice.WrongRef;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 「錯題本」分頁：答錯（含部分給分、未作答）的題目會自動加入，只能手動移除（見 DECISIONS.md D-024）。
 * 這裡顯示正確答案與解說：使用者交卷後已經看過答案。
 */
final class WrongBookPane {

    private final GuiSession session;
    private final Consumer<SubjectInfo> onReview;
    private final VBox root = new VBox(10);
    private final ComboBox<SubjectInfo> subject = new ComboBox<>();
    private final TableView<WrongItem> table = new TableView<>();
    private final TextArea preview = new TextArea();
    private final Label count = new Label();

    /** @param onReview 按「複習錯題」時呼叫，參數為目前的分類篩選（全部時為 null） */
    WrongBookPane(GuiSession session, Consumer<SubjectInfo> onReview) {
        this.session = session;
        this.onReview = onReview;

        Label heading = new Label("錯題本");
        heading.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        subject.setConverter(SubjectCells.CONVERTER);
        subject.valueProperty().addListener((obs, a, b) -> {
            if (b != null) {
                loadItems();
            }
        });

        ZoneId zone = ZoneId.systemDefault();
        table.getColumns().add(column("題目", 320,
                w -> "［" + QuestionTypes.label(w.question().type()) + "］" + w.question().content().lines().findFirst().orElse("")));
        table.getColumns().add(column("分類", 80, w -> w.subject() == null ? "" : w.subject().name()));
        table.getColumns().add(column("考卷", 160, w -> w.examTitle() == null ? "" : w.examTitle()));
        table.getColumns().add(column("錯誤次數", 70, w -> String.valueOf(w.wrongCount())));
        table.getColumns().add(column("最近答錯", 130, w -> ResultText.dateTime(w.lastWrongAt(), zone)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new Label("錯題本是空的"));
        table.getSelectionModel().selectedItemProperty().addListener((obs, a, b) -> showPreview(b));
        VBox.setVgrow(table, Priority.ALWAYS);

        preview.setEditable(false);
        preview.setWrapText(true);
        preview.setPrefRowCount(8);

        Button remove = new Button("移出錯題本");
        remove.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        remove.setOnAction(e -> {
            WrongItem item = table.getSelectionModel().getSelectedItem();
            if (Dialogs.confirm("確定要把這題移出錯題本嗎？")) {
                session.call(MessageType.REMOVE_WRONG, new WrongRef(item.questionId()), WrongRef.class, r -> loadItems());
            }
        });
        Button review = new Button("複習錯題");
        review.setStyle("-fx-font-weight: bold;");
        review.disableProperty().bind(javafx.beans.binding.Bindings.isEmpty(table.getItems()));
        review.setOnAction(e -> {
            SubjectInfo s = subject.getValue();
            onReview.accept(s == null || s == SubjectCells.ALL ? null : s);
        });
        Button refresh = new Button("重新整理");
        refresh.setOnAction(e -> refresh());

        root.getChildren().addAll(heading,
                new HBox(10, new Label("分類"), subject, count),
                table, new HBox(10, review, remove, refresh), new Label("正確答案與解說"), preview);
        root.setPadding(new Insets(16));
    }

    Parent root() {
        return root;
    }

    void refresh() {
        Long keep = subject.getValue() == null ? null : subject.getValue().id();
        session.call(MessageType.LIST_SUBJECTS, null, SubjectList.class, list -> {
            List<SubjectInfo> items = new ArrayList<>();
            items.add(SubjectCells.ALL);
            items.addAll(list.subjects());
            SubjectInfo target = items.stream().filter(s -> keep != null && s.id() == keep).findFirst()
                    .orElse(SubjectCells.ALL);
            subject.getItems().setAll(items);
            if (target.equals(subject.getValue())) {
                loadItems();
            } else {
                subject.setValue(target);
            }
        });
    }

    private void loadItems() {
        SubjectInfo s = subject.getValue();
        Long subjectId = s == null || s == SubjectCells.ALL ? null : s.id();
        session.call(MessageType.LIST_WRONG, new ListWrongRequest(subjectId), WrongList.class, list -> {
            table.getItems().setAll(list.items());
            count.setText("共 " + list.items().size() + " 題");
            preview.clear();
        });
    }

    private void showPreview(WrongItem item) {
        if (item == null) {
            preview.clear();
            return;
        }
        QuestionData q = item.question();
        StringBuilder text = new StringBuilder();
        if (q.passage() != null) {
            text.append("【題組文章】\n").append(q.passage()).append("\n\n");
        }
        text.append("［" + QuestionTypes.label(q.type()) + "題］").append(q.content()).append("\n\n");
        if (q.type() != QuestionType.TRUE_FALSE) {
            for (int i = 0; i < q.options().size(); i++) {
                boolean correct = Boolean.TRUE.equals(q.options().get(i).correct());
                text.append(correct ? "✔ " : "　 ").append(ResultText.letter(i)).append(". ")
                        .append(q.options().get(i).content()).append("\n");
            }
        }
        text.append("\n正確答案：").append(ResultText.correctAnswer(q));
        if (q.explanation() != null && !q.explanation().isBlank()) {
            text.append("\n\n解說：").append(q.explanation());
        }
        text.append("\n\n已答錯 ").append(item.wrongCount()).append(" 次");
        preview.setText(text.toString());
    }

    private static TableColumn<WrongItem, String> column(String title, double width, Function<WrongItem, String> value) {
        TableColumn<WrongItem, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        c.setPrefWidth(width);
        return c;
    }
}
