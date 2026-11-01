package com.exambattle.client.gui;

import com.exambattle.client.gui.model.PracticeSession;
import com.exambattle.client.gui.model.QuestionTypes;
import com.exambattle.client.gui.model.ResultText;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.bank.ExamList;
import com.exambattle.common.protocol.bank.ExamSummary;
import com.exambattle.common.protocol.bank.ListExamsRequest;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.bank.SubjectInfo;
import com.exambattle.common.protocol.bank.SubjectList;
import com.exambattle.common.protocol.practice.AnswerRequest;
import com.exambattle.common.protocol.practice.AnswerResult;
import com.exambattle.common.protocol.practice.PracticeMode;
import com.exambattle.common.protocol.practice.PracticeOption;
import com.exambattle.common.protocol.practice.PracticeQuestion;
import com.exambattle.common.protocol.practice.PracticeResult;
import com.exambattle.common.protocol.practice.PracticeStarted;
import com.exambattle.common.protocol.practice.RevealMode;
import com.exambattle.common.protocol.practice.StartPracticeRequest;
import com.exambattle.common.protocol.practice.SubmitRequest;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 「練習」分頁：設定 → 作答 → 結果。
 * 題目、對錯與分數都由伺服器決定；經過時間只供參考，正式作答時間以伺服器紀錄為準。
 */
final class PracticePane {

    private static final ExamSummary ANY_EXAM =
            new ExamSummary(-1, "（不限考卷）", null, null, null, null, 0, 0);

    private final GuiSession session;
    private final BorderPane root = new BorderPane();
    private final Runnable onFinished;

    // 設定畫面
    private final ToggleGroup modeGroup = new ToggleGroup();
    private final RadioButton fullExam = new RadioButton("完整考卷");
    private final RadioButton random = new RadioButton("隨機抽題");
    private final RadioButton wrongReview = new RadioButton("錯題複習");
    private final ToggleGroup revealGroup = new ToggleGroup();
    private final RadioButton examMode = new RadioButton("考試模式：可以前後翻題修改，交卷後一次公布答案");
    private final RadioButton practiceMode = new RadioButton("練習模式：每題送出後立即公布對錯與解說，不能修改");
    private final ComboBox<ExamSummary> exam = new ComboBox<>();
    private final ComboBox<SubjectInfo> subject = new ComboBox<>();
    private final Spinner<Integer> count = new Spinner<>(1, Protocol.MAX_PRACTICE_COUNT, Protocol.DEFAULT_PRACTICE_COUNT);
    private final Label setupHint = new Label();
    private final Parent setupView;

    // 作答中
    private PracticeSession practice;
    private Timeline timer;
    /** 題組文章是否展開；換題時沿用使用者的選擇。 */
    private boolean passageExpanded = true;

    /** @param onFinished 交卷完成後呼叫（例如讓紀錄、錯題本重新整理） */
    PracticePane(GuiSession session, Runnable onFinished) {
        this.session = session;
        this.onFinished = onFinished;
        this.setupView = buildSetup();
        showSetup();
    }

    Parent root() {
        return root;
    }

    /** 重新載入考卷與分類清單（切換到本分頁時呼叫）。 */
    void refreshSources() {
        Long keepExam = exam.getValue() == null ? null : exam.getValue().id();
        Long keepSubject = subject.getValue() == null ? null : subject.getValue().id();
        session.call(MessageType.LIST_EXAMS, new ListExamsRequest(null), ExamList.class, list -> {
            List<ExamSummary> items = new ArrayList<>();
            items.add(ANY_EXAM);
            items.addAll(list.exams());
            exam.getItems().setAll(items);
            exam.setValue(items.stream().filter(e -> keepExam != null && e.id() == keepExam).findFirst()
                    .orElse(items.size() > 1 ? items.get(1) : ANY_EXAM));
        });
        session.call(MessageType.LIST_SUBJECTS, null, SubjectList.class, list -> {
            List<SubjectInfo> items = new ArrayList<>();
            items.add(SubjectCells.ALL);
            items.addAll(list.subjects());
            subject.getItems().setAll(items);
            subject.setValue(items.stream().filter(s -> keepSubject != null && s.id() == keepSubject).findFirst()
                    .orElse(SubjectCells.ALL));
        });
    }

    /** 從錯題本進入：預先選好「錯題複習」與分類，回到設定畫面讓使用者選擇模式後開始。 */
    void prepareWrongReview(SubjectInfo filter) {
        if (practice != null && !Dialogs.confirm("目前的練習尚未交卷，要放棄並改為複習錯題嗎？")) {
            return;
        }
        abandon();
        wrongReview.setSelected(true);
        // 清單可能尚未載入：直接設定，切換到本分頁時 refreshSources() 會依 id 對回清單中的項目
        subject.setValue(filter == null ? SubjectCells.ALL : filter);
        showSetup();
    }

    // ---------- 設定

    private Parent buildSetup() {
        Label heading = new Label("開始練習");
        heading.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        for (RadioButton b : List.of(fullExam, random, wrongReview)) {
            b.setToggleGroup(modeGroup);
        }
        fullExam.setSelected(true);
        examMode.setToggleGroup(revealGroup);
        practiceMode.setToggleGroup(revealGroup);
        examMode.setSelected(true);

        exam.setConverter(new StringConverter<>() {
            @Override
            public String toString(ExamSummary e) {
                if (e == null) {
                    return "";
                }
                return e == ANY_EXAM ? e.title() : e.title() + "（" + e.subject().name() + "，"
                        + (e.questionCount() - e.unansweredCount()) + " 題可練習）";
            }

            @Override
            public ExamSummary fromString(String s) {
                throw new UnsupportedOperationException();
            }
        });
        exam.setMaxWidth(Double.MAX_VALUE);
        subject.setConverter(SubjectCells.CONVERTER);
        count.setEditable(true);
        count.setPrefWidth(100);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.addRow(0, new Label("練習方式"), new HBox(16, fullExam, random, wrongReview));
        form.addRow(1, new Label("考卷"), exam);
        form.addRow(2, new Label("分類"), subject);
        form.addRow(3, new Label("題數"), count);
        form.addRow(4, new Label("答案公布"), new VBox(6, examMode, practiceMode));
        GridPane.setHgrow(exam, Priority.ALWAYS);

        setupHint.setWrapText(true);
        setupHint.setStyle("-fx-text-fill: #555;");
        modeGroup.selectedToggleProperty().addListener((obs, a, b) -> updateSetupFields());
        exam.valueProperty().addListener((obs, a, b) -> updateSetupFields());
        updateSetupFields();

        Button start = new Button("開始");
        start.setDefaultButton(true);
        start.setPrefWidth(120);
        start.setOnAction(e -> start());
        Button refresh = new Button("重新整理清單");
        refresh.setOnAction(e -> refreshSources());

        VBox box = new VBox(16, heading, form, setupHint, new HBox(10, start, refresh),
                new Label("只會出現已設定正確答案的題目。計分、對錯與作答時間由伺服器決定。"));
        box.setPadding(new Insets(20));
        box.setMaxWidth(760);
        return box;
    }

    private void updateSetupFields() {
        boolean isFull = fullExam.isSelected();
        boolean isRandom = random.isSelected();
        exam.setDisable(!(isFull || isRandom));
        boolean examChosen = exam.getValue() != null && exam.getValue() != ANY_EXAM;
        subject.setDisable(isFull || (isRandom && examChosen));
        count.setDisable(!isRandom);
        if (isFull) {
            setupHint.setText("依考卷的題目順序作答全部題目。");
        } else if (isRandom) {
            setupHint.setText("從選定的考卷隨機抽題；選「（不限考卷）」時改從分類抽題，分類選「（全部）」則從全部題庫抽題。");
        } else {
            setupHint.setText("練習錯題本中的題目（題目順序隨機）。答對也不會自動移出錯題本，需要時請到「錯題本」手動移除。");
        }
    }

    private void start() {
        PracticeMode mode = fullExam.isSelected() ? PracticeMode.FULL_EXAM
                : random.isSelected() ? PracticeMode.RANDOM : PracticeMode.WRONG_REVIEW;
        RevealMode reveal = examMode.isSelected() ? RevealMode.AFTER_SUBMIT : RevealMode.IMMEDIATE;
        ExamSummary e = exam.getValue();
        Long examId = e == null || e == ANY_EXAM || mode == PracticeMode.WRONG_REVIEW ? null : e.id();
        SubjectInfo s = subject.getValue();
        Long subjectId = s == null || s == SubjectCells.ALL || mode == PracticeMode.FULL_EXAM || examId != null
                ? null : s.id();
        if (mode == PracticeMode.FULL_EXAM && examId == null) {
            Dialogs.error("請選擇一份考卷");
            return;
        }
        Integer n = mode == PracticeMode.RANDOM ? count.getValue() : null;
        session.call(MessageType.START_PRACTICE, new StartPracticeRequest(mode, reveal, examId, subjectId, n),
                PracticeStarted.class, started -> {
                    practice = new PracticeSession(started);
                    showQuestion();
                });
    }

    private void showSetup() {
        stopTimer();
        root.setTop(null);
        root.setBottom(null);
        ScrollPane scroll = new ScrollPane(setupView);
        scroll.setFitToWidth(true);
        root.setCenter(scroll);
    }

    // ---------- 作答

    private void showQuestion() {
        PracticeStarted started = practice.started();
        PracticeQuestion q = practice.question();
        int index = practice.current();

        Label title = new Label(started.title() + "　（" + ResultText.reveal(started.revealMode()) + "）");
        title.setStyle("-fx-font-weight: bold;");
        Label progress = new Label("第 " + (index + 1) + " / " + practice.size() + " 題");
        Label elapsed = new Label();
        startTimer(elapsed);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(16, title, spacer, progress, elapsed);
        top.setPadding(new Insets(10, 14, 10, 14));
        top.setStyle("-fx-background-color: #eef2f7;");
        root.setTop(top);

        Label type = new Label("［" + QuestionTypes.label(q.type()) + "題］"
                + (q.type() == QuestionType.MULTIPLE_CHOICE ? "（可複選，依學測規則部分給分）" : ""));
        type.setStyle("-fx-text-fill: #555;");
        Label content = new Label(q.content());
        content.setWrapText(true);
        content.setStyle("-fx-font-size: 16px;");

        VBox optionsBox = new VBox(10);
        boolean locked = practice.locked(index);
        if (q.type() == QuestionType.TRUE_FALSE) {
            ToggleGroup group = new ToggleGroup();
            RadioButton yes = new RadioButton("對");
            RadioButton no = new RadioButton("錯");
            yes.setToggleGroup(group);
            no.setToggleGroup(group);
            Boolean current = practice.trueFalse(index);
            yes.setSelected(Boolean.TRUE.equals(current));
            no.setSelected(Boolean.FALSE.equals(current));
            group.selectedToggleProperty().addListener((obs, a, b) ->
                    practice.setTrueFalse(index, b == yes ? Boolean.TRUE : b == no ? Boolean.FALSE : null));
            yes.setDisable(locked);
            no.setDisable(locked);
            optionsBox.getChildren().addAll(yes, no);
        } else if (q.type() == QuestionType.SINGLE_CHOICE) {
            ToggleGroup group = new ToggleGroup();
            for (int i = 0; i < q.options().size(); i++) {
                PracticeOption o = q.options().get(i);
                RadioButton b = new RadioButton(ResultText.letter(i) + ". " + o.content());
                b.setWrapText(true);
                b.setToggleGroup(group);
                b.setUserData(o.id());
                b.setSelected(practice.choices(index).contains(o.id()));
                b.setDisable(locked);
                optionsBox.getChildren().add(b);
            }
            group.selectedToggleProperty().addListener((obs, a, b) ->
                    practice.setChoices(index, b == null ? Set.of() : Set.of((Long) b.getUserData())));
        } else {
            List<CheckBox> boxes = new ArrayList<>();
            for (int i = 0; i < q.options().size(); i++) {
                PracticeOption o = q.options().get(i);
                CheckBox b = new CheckBox(ResultText.letter(i) + ". " + o.content());
                b.setWrapText(true);
                b.setUserData(o.id());
                b.setSelected(practice.choices(index).contains(o.id()));
                b.setDisable(locked);
                boxes.add(b);
                b.selectedProperty().addListener((obs, a, now) -> {
                    Set<Long> ids = new LinkedHashSet<>();
                    boxes.stream().filter(CheckBox::isSelected).forEach(c -> ids.add((Long) c.getUserData()));
                    practice.setChoices(index, ids);
                });
            }
            optionsBox.getChildren().addAll(boxes);
        }

        VBox feedback = new VBox(6);
        if (practice.result(index) != null) {
            showFeedback(feedback, q, practice.result(index));
        }

        VBox body = new VBox(14, type);
        if (q.passage() != null) {
            javafx.scene.control.TitledPane passage = PassageView.create(q.passage(), 15, passageExpanded);
            passage.expandedProperty().addListener((obs, a, b) -> passageExpanded = b);
            body.getChildren().add(passage);
        }
        body.getChildren().addAll(content, optionsBox, feedback);
        body.setPadding(new Insets(20));
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        root.setCenter(scroll);
        root.setBottom(practice.immediate() ? immediateButtons(feedback) : examButtons());
    }

    /** 考試模式：上一題、下一題、題號、交卷。 */
    private Parent examButtons() {
        Button previous = new Button("上一題");
        previous.setDisable(!practice.hasPrevious());
        previous.setOnAction(e -> move(-1));
        Button next = new Button("下一題");
        next.setDisable(!practice.hasNext());
        next.setOnAction(e -> move(1));
        Button submit = new Button("交卷");
        submit.setStyle("-fx-font-weight: bold;");
        submit.setOnAction(e -> submit());

        FlowPane numbers = new FlowPane(4, 4);
        for (int i = 0; i < practice.size(); i++) {
            int target = i;
            Button b = new Button(String.valueOf(i + 1));
            b.setMinWidth(34);
            String color = i == practice.current() ? "#1565c0" : practice.isAnswered(i) ? "#90caf9" : "#eeeeee";
            b.setStyle("-fx-background-color: " + color + ";" + (i == practice.current() ? "-fx-text-fill: white;" : ""));
            b.setOnAction(e -> {
                practice.goTo(target);
                showQuestion();
            });
            numbers.getChildren().add(b);
        }
        Label legend = new Label("藍色：已作答");
        legend.setStyle("-fx-text-fill: #555;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox controls = new HBox(10, previous, next, spacer, submit);
        VBox bottom = new VBox(8, controls, numbers, legend);
        bottom.setPadding(new Insets(10, 14, 12, 14));
        return bottom;
    }

    /** 練習模式：送出答案 → 顯示結果 → 下一題；也可以提前結束。 */
    private Parent immediateButtons(VBox feedback) {
        int index = practice.current();
        boolean answered = practice.locked(index);
        Button send = new Button("送出答案");
        send.setDefaultButton(true);
        send.setDisable(answered);
        Button next = new Button(practice.hasNext() ? "下一題" : "查看結果");
        next.setDisable(!answered);
        next.setOnAction(e -> {
            if (practice.hasNext()) {
                move(1);
            } else {
                finish();
            }
        });
        send.setOnAction(e -> {
            if (!practice.isAnswered(index)) {
                Dialogs.error("請先選擇答案");
                return;
            }
            var a = practice.answer(index);
            session.call(MessageType.ANSWER_QUESTION,
                    new AnswerRequest(practice.started().practiceId(), index, a.selectedOptionIds(), a.trueFalseAnswer()),
                    AnswerResult.class, result -> {
                        practice.recordResult(result);
                        showQuestion();
                    });
        });
        Button end = new Button("結束練習");
        end.setOnAction(e -> {
            int remaining = practice.size() - (int) java.util.stream.IntStream.range(0, practice.size())
                    .filter(practice::locked).count();
            if (remaining == 0 || Dialogs.confirm("還有 " + remaining + " 題未作答，將以 0 分計算。確定要結束嗎？")) {
                finish();
            }
        });
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bottom = new HBox(10, send, next, spacer, end);
        bottom.setPadding(new Insets(10, 14, 12, 14));
        bottom.setAlignment(Pos.CENTER_LEFT);
        return bottom;
    }

    private void showFeedback(VBox feedback, PracticeQuestion q, AnswerResult r) {
        String verdict = r.correct() ? "✔ 答對了！" : r.score() > 0 ? "△ 部分給分" : "✘ 答錯了";
        Label head = new Label(verdict + "　本題得分 " + ResultText.questionScore(r.score()));
        head.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: "
                + (r.correct() ? "#2e7d32" : "#c62828") + ";");
        String correct;
        if (q.type() == QuestionType.TRUE_FALSE) {
            correct = ResultText.trueFalse(r.correctTrueFalse());
        } else {
            List<String> letters = new ArrayList<>();
            for (int i = 0; i < q.options().size(); i++) {
                if (r.correctOptionIds().contains(q.options().get(i).id())) {
                    letters.add(ResultText.letter(i));
                }
            }
            correct = String.join("、", letters);
        }
        feedback.getChildren().add(head);
        feedback.getChildren().add(new Label("正確答案：" + correct));
        if (r.explanation() != null && !r.explanation().isBlank()) {
            Label exp = new Label("解說：" + r.explanation());
            exp.setWrapText(true);
            feedback.getChildren().add(exp);
        }
        feedback.setPadding(new Insets(10));
        feedback.setStyle("-fx-background-color: " + (r.correct() ? "#e8f5e9" : "#ffebee") + ";");
    }

    private void move(int delta) {
        practice.goTo(practice.current() + delta);
        showQuestion();
    }

    private void submit() {
        int unanswered = practice.unansweredCount();
        String message = unanswered == 0 ? "確定要交卷嗎？"
                : "還有 " + unanswered + " 題未作答（以 0 分計算並列入錯題）。確定要交卷嗎？";
        if (Dialogs.confirm(message)) {
            finish();
        }
    }

    /** 交卷：考試模式送出全部作答；練習模式送出空清單（伺服器以已批改的為準）。 */
    private void finish() {
        List<com.exambattle.common.protocol.practice.AnswerData> answers =
                practice.immediate() ? List.of() : practice.answersToSubmit();
        session.call(MessageType.SUBMIT_PRACTICE, new SubmitRequest(practice.started().practiceId(), answers),
                PracticeResult.class, this::showResult);
    }

    private void showResult(PracticeResult result) {
        stopTimer();
        practice = null;
        Button again = new Button("再練一次");
        again.setOnAction(e -> start());
        Button back = new Button("回到設定");
        back.setOnAction(e -> showSetup());
        root.setTop(null);
        root.setBottom(null);
        root.setCenter(new ResultView(result, again, back).root());
        onFinished.run();
    }

    private void abandon() {
        practice = null;
        stopTimer();
    }

    private void startTimer(Label label) {
        stopTimer();
        long startedAt = java.time.Instant.parse(practice.started().startedAt()).toEpochMilli();
        Runnable update = () -> label.setText("經過 " + ResultText.duration(System.currentTimeMillis() - startedAt));
        update.run();
        timer = new Timeline(new KeyFrame(Duration.seconds(1), e -> update.run()));
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }

    private void stopTimer() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }
}
