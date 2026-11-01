package com.exambattle.client.gui;

import com.exambattle.client.gui.model.QuestionGroups;
import com.exambattle.client.gui.model.QuestionTypes;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.bank.CreateSubjectRequest;
import com.exambattle.common.protocol.bank.ExamDetail;
import com.exambattle.common.protocol.bank.ExamList;
import com.exambattle.common.protocol.bank.ExamRef;
import com.exambattle.common.protocol.bank.ExamSummary;
import com.exambattle.common.protocol.bank.GroupRequest;
import com.exambattle.common.protocol.bank.GroupSaved;
import com.exambattle.common.protocol.bank.ListExamsRequest;
import com.exambattle.common.protocol.bank.OptionData;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionRef;
import com.exambattle.common.protocol.bank.QuestionRequest;
import com.exambattle.common.protocol.bank.QuestionSaved;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.bank.ReorderRequest;
import com.exambattle.common.protocol.bank.SubjectInfo;
import com.exambattle.common.protocol.bank.SubjectList;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Parent;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * 題庫分頁：左側大分類、中間考卷清單、右側考卷內容與題目。
 * 所有登入者都能瀏覽；只有考卷建立者能修改（按鈕會停用，伺服器也會檢查）。
 */
final class BankPane {

    private final GuiSession session;
    private final BorderPane root = new BorderPane();
    private final SplitPane split = new SplitPane();
    private final ListView<SubjectInfo> subjects = new ListView<>();
    private final TableView<ExamSummary> exams = new TableView<>();
    private final ListView<QuestionData> questions = new ListView<>();
    private final TextArea preview = new TextArea();
    private final Label examTitle = new Label("請從左側選擇一份考卷");
    private final ObjectProperty<ExamDetail> detail = new SimpleObjectProperty<>();
    /** 目前考卷中的題組（標示依題目位置）。 */
    private List<QuestionGroups.Group> groups = List.of();

    BankPane(GuiSession session) {
        this.session = session;
        split.getItems().addAll(examColumn(), detailColumn());
        split.setDividerPositions(0.58);
        split.setOrientation(Orientation.HORIZONTAL);
        root.setLeft(subjectColumn());
        root.setCenter(split);
        loadSubjects(null);
    }

    Parent root() {
        return root;
    }

    // ---------- 左：大分類

    private Parent subjectColumn() {
        subjects.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(SubjectInfo s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s.name());
            }
        });
        subjects.getSelectionModel().selectedItemProperty().addListener((obs, before, after) -> loadExams());
        Button add = new Button("新增分類");
        add.setMaxWidth(Double.MAX_VALUE);
        add.setOnAction(e -> {
            TextInputDialog input = new TextInputDialog();
            input.setTitle("新增分類");
            input.setHeaderText("大分類名稱（全站共用，例如：物理、日文）");
            input.showAndWait().filter(name -> !name.isBlank()).ifPresent(name ->
                    session.call(MessageType.CREATE_SUBJECT, new CreateSubjectRequest(name), SubjectInfo.class,
                            created -> loadSubjects(created.id())));
        });
        VBox box = MainView.titled("分類", subjects);
        box.getChildren().add(add);
        box.setPrefWidth(170);
        return box;
    }

    private void loadSubjects(Long selectId) {
        session.call(MessageType.LIST_SUBJECTS, null, SubjectList.class, list -> {
            List<SubjectInfo> items = new ArrayList<>();
            items.add(SubjectCells.ALL);
            items.addAll(list.subjects());
            subjects.getItems().setAll(items);
            SubjectInfo target = items.stream()
                    .filter(s -> selectId != null && s.id() == selectId)
                    .findFirst().orElse(SubjectCells.ALL);
            subjects.getSelectionModel().select(target);
        });
    }

    private List<SubjectInfo> realSubjects() {
        return subjects.getItems().stream().filter(s -> s != SubjectCells.ALL).toList();
    }

    // ---------- 中：考卷清單

    private Parent examColumn() {
        exams.getColumns().add(column("考卷名稱", 200, 120, ExamSummary::title));
        exams.getColumns().add(column("分類", 80, 60, e -> e.subject().name()));
        exams.getColumns().add(column("年份", 55, 55, e -> e.year() == null ? "" : e.year().toString()));
        exams.getColumns().add(column("類型", 70, 50, e -> e.examType() == null ? "" : e.examType()));
        exams.getColumns().add(column("建立者", 80, 60, e -> e.owner().displayName()));
        exams.getColumns().add(column("題數", 50, 50, e -> String.valueOf(e.questionCount())));
        exams.getColumns().add(column("未設答案", 75, 75, e -> e.unansweredCount() == 0 ? "" : String.valueOf(e.unansweredCount())));
        exams.setPlaceholder(new Label("這個分類還沒有考卷"));
        exams.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        exams.getSelectionModel().selectedItemProperty().addListener((obs, before, after) -> loadDetail(after));

        Button create = new Button("新增考卷");
        create.setOnAction(e -> {
            SubjectInfo selected = subjects.getSelectionModel().getSelectedItem();
            ExamDialog.show(realSubjects(), null, selected == SubjectCells.ALL ? null : selected).ifPresent(request ->
                    session.call(MessageType.CREATE_EXAM, request, ExamSummary.class, saved -> loadExams(saved.id())));
        });
        Button edit = new Button("編輯考卷");
        edit.setOnAction(e -> {
            ExamSummary exam = exams.getSelectionModel().getSelectedItem();
            ExamDialog.show(realSubjects(), exam, null).ifPresent(request ->
                    session.call(MessageType.UPDATE_EXAM, request, ExamSummary.class, saved -> loadExams(saved.id())));
        });
        Button delete = new Button("刪除考卷");
        delete.setOnAction(e -> {
            ExamSummary exam = exams.getSelectionModel().getSelectedItem();
            if (Dialogs.confirm("確定要刪除「" + exam.title() + "」與其中的 " + exam.questionCount() + " 道題目嗎？")) {
                session.call(MessageType.DELETE_EXAM, new ExamRef(exam.id()), ExamRef.class, deleted -> loadExams());
            }
        });
        Button refresh = new Button("重新整理");
        refresh.setOnAction(e -> loadExams());

        BooleanBinding notMine = Bindings.createBooleanBinding(
                () -> !isMine(exams.getSelectionModel().getSelectedItem()),
                exams.getSelectionModel().selectedItemProperty());
        edit.disableProperty().bind(notMine);
        delete.disableProperty().bind(notMine);

        VBox box = MainView.titled("考卷", exams);
        box.getChildren().add(new FlowPane(8, 8, create, edit, delete, refresh));
        return box;
    }

    private static TableColumn<ExamSummary, String> column(String title, double width, double minWidth,
                                                           java.util.function.Function<ExamSummary, String> value) {
        TableColumn<ExamSummary, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        c.setPrefWidth(width);
        c.setMinWidth(minWidth);
        return c;
    }

    private void loadExams() {
        ExamSummary selected = exams.getSelectionModel().getSelectedItem();
        loadExams(selected == null ? null : selected.id());
    }

    private void loadExams(Long selectId) {
        SubjectInfo subject = subjects.getSelectionModel().getSelectedItem();
        Long subjectId = subject == null || subject == SubjectCells.ALL ? null : subject.id();
        session.call(MessageType.LIST_EXAMS, new ListExamsRequest(subjectId), ExamList.class, list -> {
            exams.getItems().setAll(list.exams());
            list.exams().stream().filter(e -> selectId != null && e.id() == selectId).findFirst()
                    .ifPresentOrElse(e -> exams.getSelectionModel().select(e), () -> detail.set(null));
        });
    }

    private boolean isMine(ExamSummary exam) {
        return exam != null && session.state().me() != null && exam.owner().userId() == session.state().me().userId();
    }

    // ---------- 右：考卷內容與題目

    private Parent detailColumn() {
        examTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        examTitle.setWrapText(true);
        examTitle.setMinHeight(Region.USE_PREF_SIZE);
        questions.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(QuestionData q, boolean empty) {
                super.updateItem(q, empty);
                if (empty || q == null) {
                    setText(null);
                    return;
                }
                int index = getIndex() + 1;
                String firstLine = q.content().lines().findFirst().orElse("");
                String group = q.groupId() == null ? "" : "〔" + QuestionGroups.labelOf(groups, q.groupId()) + "〕";
                setText(index + ". " + group + "[" + QuestionTypes.label(q.type()) + "] " + firstLine
                        + (q.answerSet() ? "" : "　⚠ 答案未設定"));
            }
        });
        questions.setPlaceholder(new Label("尚無題目"));
        questions.getSelectionModel().selectedItemProperty().addListener((obs, before, after) -> showPreview(after));
        preview.setEditable(false);
        preview.setWrapText(true);
        preview.setPrefRowCount(8);

        Button add = new Button("新增題目");
        add.setOnAction(e -> QuestionDialog.show(null, groups).ifPresent(q -> {
            long examId = detail.get().exam().id();
            session.call(MessageType.ADD_QUESTION, new QuestionRequest(examId, null, q), QuestionSaved.class,
                    saved -> reloadDetail(saved.question().id()));
        }));
        Button edit = new Button("編輯");
        edit.setOnAction(e -> {
            QuestionData selected = questions.getSelectionModel().getSelectedItem();
            QuestionDialog.show(selected, groups).ifPresent(q -> {
                long examId = detail.get().exam().id();
                session.call(MessageType.UPDATE_QUESTION, new QuestionRequest(examId, selected.id(), q),
                        QuestionSaved.class, saved -> reloadDetail(saved.question().id()));
            });
        });
        Button delete = new Button("刪除");
        delete.setOnAction(e -> {
            QuestionData selected = questions.getSelectionModel().getSelectedItem();
            if (Dialogs.confirm("確定要刪除這道題目嗎？")) {
                session.call(MessageType.DELETE_QUESTION, new QuestionRef(detail.get().exam().id(), selected.id()),
                        QuestionRef.class, deleted -> reloadDetail(null));
            }
        });
        Button editPassage = new Button("編輯題組文章");
        editPassage.setOnAction(e -> editPassage(questions.getSelectionModel().getSelectedItem()));
        Button up = new Button("上移");
        up.setOnAction(e -> move(-1));
        Button down = new Button("下移");
        down.setOnAction(e -> move(1));

        BooleanBinding notOwner = Bindings.createBooleanBinding(
                () -> detail.get() == null || !isMine(detail.get().exam()), detail);
        BooleanBinding noQuestion = questions.getSelectionModel().selectedItemProperty().isNull();
        add.disableProperty().bind(notOwner);
        for (Button b : List.of(edit, delete, up, down)) {
            b.disableProperty().bind(notOwner.or(noQuestion));
        }
        editPassage.disableProperty().bind(notOwner.or(Bindings.createBooleanBinding(() -> {
            QuestionData q = questions.getSelectionModel().getSelectedItem();
            return q == null || q.groupId() == null;
        }, questions.getSelectionModel().selectedItemProperty())));

        Label readOnly = new Label("你不是建立者：只能瀏覽，看不到正確答案。");
        readOnly.setStyle("-fx-text-fill: #555;");
        readOnly.visibleProperty().bind(detail.isNotNull().and(notOwner));
        readOnly.managedProperty().bind(readOnly.visibleProperty());

        detail.addListener((obs, before, after) -> {
            examTitle.setText(after == null ? "請從左側選擇一份考卷" : describe(after.exam()));
            groups = after == null ? List.of() : QuestionGroups.of(after.questions());
            questions.getItems().setAll(after == null ? List.of() : after.questions());
        });

        VBox.setVgrow(questions, Priority.ALWAYS);
        VBox box = new VBox(8, examTitle, readOnly, questions,
                new FlowPane(8, 8, add, edit, delete, up, down, editPassage),
                new Label("題目內容"), preview);
        box.setPadding(new Insets(12));
        return box;
    }

    private static String describe(ExamSummary exam) {
        StringBuilder text = new StringBuilder(exam.title()).append("\n").append(exam.subject().name());
        if (exam.year() != null) {
            text.append("・").append(exam.year());
        }
        if (exam.examType() != null) {
            text.append("・").append(exam.examType());
        }
        text.append("・建立者 ").append(exam.owner().displayName()).append("・").append(exam.questionCount()).append(" 題");
        if (exam.unansweredCount() > 0) {
            text.append("（").append(exam.unansweredCount()).append(" 題未設答案）");
        }
        return text.toString();
    }

    private void loadDetail(ExamSummary exam) {
        if (exam == null) {
            detail.set(null);
            return;
        }
        session.call(MessageType.GET_EXAM, new ExamRef(exam.id()), ExamDetail.class, detail::set);
    }

    /** 重新載入考卷內容（並更新清單中的題數），之後選取指定題目。 */
    private void reloadDetail(Long selectQuestionId) {
        long examId = detail.get().exam().id();
        session.call(MessageType.GET_EXAM, new ExamRef(examId), ExamDetail.class, loaded -> {
            detail.set(loaded);
            select(selectQuestionId);
            exams.getItems().replaceAll(e -> e.id() == examId ? loaded.exam() : e);
        });
    }

    /** 修改題組文章；同組的題目一起改變（UPDATE_GROUP）。 */
    private void editPassage(QuestionData q) {
        if (q == null || q.groupId() == null) {
            return;
        }
        TextArea text = new TextArea(q.passage());
        text.setWrapText(true);
        text.setPrefRowCount(16);
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("編輯題組文章");
        dialog.setHeaderText(QuestionGroups.labelOf(groups, q.groupId()) + "：修改後同組的題目一起改變（克漏字的空格可寫成【11】）");
        dialog.setResizable(true);
        dialog.getDialogPane().setContent(text);
        dialog.getDialogPane().setPrefWidth(640);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e ->
                QuestionDialog.passageError(text.getText()).ifPresent(error -> {
                    Dialogs.error(error);
                    e.consume();
                }));
        dialog.setResultConverter(b -> b == ButtonType.OK ? text.getText() : null);
        dialog.showAndWait().ifPresent(passage ->
                session.call(MessageType.UPDATE_GROUP, new GroupRequest(detail.get().exam().id(), q.groupId(), passage),
                        GroupSaved.class, saved -> reloadDetail(q.id())));
    }

    private void move(int delta) {
        int index = questions.getSelectionModel().getSelectedIndex();
        int target = index + delta;
        List<QuestionData> items = questions.getItems();
        if (index < 0 || target < 0 || target >= items.size()) {
            return;
        }
        List<Long> ids = new ArrayList<>(items.stream().map(QuestionData::id).toList());
        long moved = ids.remove(index);
        ids.add(target, moved);
        session.call(MessageType.REORDER_QUESTIONS, new ReorderRequest(detail.get().exam().id(), ids), ExamDetail.class,
                loaded -> {
                    detail.set(loaded);
                    select(moved);
                });
    }

    private void select(Long questionId) {
        if (questionId == null) {
            return;
        }
        questions.getItems().stream().filter(q -> q.id().equals(questionId)).findFirst()
                .ifPresent(q -> questions.getSelectionModel().select(q));
    }

    private void showPreview(QuestionData q) {
        if (q == null) {
            preview.clear();
            return;
        }
        boolean owner = detail.get() != null && isMine(detail.get().exam());
        StringBuilder text = new StringBuilder();
        if (q.passage() != null) {
            text.append("【").append(QuestionGroups.labelOf(groups, q.groupId())).append(" 文章】\n").append(q.passage())
                    .append("\n\n");
        }
        text.append("[" + QuestionTypes.label(q.type()) + "題] ").append(q.content()).append("\n\n");
        if (q.type() == QuestionType.TRUE_FALSE) {
            if (owner) {
                text.append("答案：").append(q.trueFalseAnswer() == null ? "未設定" : q.trueFalseAnswer() ? "對" : "錯");
            }
        } else {
            for (int i = 0; i < q.options().size(); i++) {
                OptionData o = q.options().get(i);
                text.append(Boolean.TRUE.equals(o.correct()) ? "✔ " : "　 ").append((char) ('A' + i)).append(". ")
                        .append(o.content()).append("\n");
            }
        }
        if (!q.answerSet()) {
            text.append("\n⚠ 尚未設定正確答案");
        } else if (!owner) {
            text.append("\n（正確答案只有建立者看得到）");
        }
        if (q.explanation() != null && owner) {
            text.append("\n\n解說：").append(q.explanation());
        }
        preview.setText(text.toString());
    }
}
