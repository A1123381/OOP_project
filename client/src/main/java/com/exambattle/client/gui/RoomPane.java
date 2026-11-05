package com.exambattle.client.gui;

import com.exambattle.client.gui.model.GameText;
import com.exambattle.client.gui.model.QuestionTypes;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.ProtocolException;
import com.exambattle.common.protocol.bank.ExamList;
import com.exambattle.common.protocol.bank.ExamSummary;
import com.exambattle.common.protocol.bank.ListExamsRequest;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.bank.SubjectInfo;
import com.exambattle.common.protocol.bank.SubjectList;
import com.exambattle.common.protocol.game.AnswerAccepted;
import com.exambattle.common.protocol.game.GameEndReason;
import com.exambattle.common.protocol.game.GameOver;
import com.exambattle.common.protocol.game.GameProgress;
import com.exambattle.common.protocol.game.GameQuestion;
import com.exambattle.common.protocol.game.GameStarted;
import com.exambattle.common.protocol.game.KickRequest;
import com.exambattle.common.protocol.game.LeaderboardEntry;
import com.exambattle.common.protocol.game.QuestionResult;
import com.exambattle.common.protocol.game.StartGameRequest;
import com.exambattle.common.protocol.game.SubmitAnswerRequest;
import com.exambattle.common.protocol.payload.LeftRoom;
import com.exambattle.common.protocol.payload.RoomState;
import com.exambattle.common.protocol.payload.UserInfo;
import com.exambattle.common.protocol.practice.PracticeOption;
import com.exambattle.common.protocol.practice.PracticeQuestion;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
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
 * 「房間」分頁（第四階段）：玩家列表與移出、房主的競賽設定、同步出題與倒數、每題結果與排行榜、最終排行榜。
 * 判分、計分、計時與排行榜一律以伺服器的訊息為準（D-037），這裡只負責顯示與送出答案。
 */
final class RoomPane {

    private static final ExamSummary ANY_EXAM = new ExamSummary(-1, "（不限考卷，從分類隨機抽題）", null, null, null, null, 0, 0);

    private final GuiSession session;
    private final ClientState state;
    private final BorderPane root = new BorderPane();
    private final BorderPane center = new BorderPane();
    private final ListView<UserInfo> players = new ListView<>();
    private final Button kick = new Button("移出玩家");
    private final Button endGame = new Button("結束競賽");

    // 房主的設定
    private final ComboBox<ExamSummary> exam = new ComboBox<>();
    private final ComboBox<SubjectInfo> subject = new ComboBox<>();
    private final RadioButton inOrder = new RadioButton("依考卷順序");
    private final RadioButton random = new RadioButton("隨機抽題");
    private final Spinner<Integer> count = new Spinner<>(1, Protocol.MAX_GAME_QUESTIONS, Protocol.DEFAULT_GAME_COUNT);
    private final CheckBox allQuestions = new CheckBox("全部題目");
    private final ComboBox<Integer> seconds = new ComboBox<>();
    private final Parent setupView;

    // 進行中的競賽
    private GameQuestion question;
    private Label progressLabel;
    private Timeline countdown;
    /** 正在顯示最終排行榜（按「回到房間」前不切回等待畫面）。 */
    private boolean showingOver;

    RoomPane(GuiSession session, Parent chat) {
        this.session = session;
        this.state = session.state();
        this.setupView = buildSetup();

        Label header = new Label();
        header.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        header.textProperty().bind(Bindings.createStringBinding(() -> {
            RoomState r = state.roomProperty().get();
            return r == null ? "尚未進入房間"
                    : "房間代碼 " + r.roomCode() + "（" + r.players().size() + "/" + r.maxPlayers() + " 人）"
                    + (r.playing() ? "　競賽進行中" : "");
        }, state.roomProperty()));
        Button leave = new Button("離開房間");
        leave.setOnAction(e -> leave());
        endGame.setOnAction(e -> {
            if (Dialogs.confirm("確定要提前結束這場競賽嗎？已公布的題目會照常計分。")) {
                session.call(MessageType.END_GAME, null, GameOver.class, over -> { });
            }
        });
        Label hint = new Label("房主離開或斷線時房間會解散。");
        hint.setStyle("-fx-text-fill: #555;");
        HBox top = new HBox(16, header, leave, endGame, hint);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10));

        players.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(UserInfo user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setText(null);
                    return;
                }
                RoomState r = state.roomProperty().get();
                String text = user.displayName() + "（" + user.username() + "）";
                if (r != null && r.ownerUserId() == user.userId()) {
                    text += "【房主】";
                }
                if (isMe(user)) {
                    text += "（你）";
                }
                setText(text);
            }
        });
        players.setPrefWidth(240);
        kick.setOnAction(e -> {
            UserInfo target = players.getSelectionModel().getSelectedItem();
            if (target != null && Dialogs.confirm("確定要把「" + target.displayName() + "」移出房間嗎？")) {
                session.call(MessageType.KICK_PLAYER, new KickRequest(target.userId()), RoomState.class, r -> { });
            }
        });
        players.getSelectionModel().selectedItemProperty().addListener((obs, a, b) -> updateControls());
        VBox left = MainView.titled("房間玩家", players);
        left.getChildren().add(kick);

        root.setTop(top);
        root.setLeft(left);
        root.setCenter(center);
        VBox chatBox = MainView.titled("房間聊天", chat);
        chatBox.setPrefWidth(300);
        root.setRight(chatBox);

        state.roomProperty().addListener((obs, before, after) -> onRoom(before, after));
        state.gameProperty().addListener((obs, before, after) -> {
            if (after != null) {
                onGame(after);
            }
        });
        onRoom(null, state.roomProperty().get());
    }

    Parent root() {
        return root;
    }

    // ---------- 房間

    private void onRoom(RoomState before, RoomState after) {
        players.getItems().setAll(after == null ? List.of() : after.players());
        if (after == null) {
            stopCountdown();
            showingOver = false;
            question = null;
            center.setCenter(null);
        } else if (before == null || !before.roomCode().equals(after.roomCode())) {
            showingOver = false;
            refreshSources();
            showWaiting();
        } else if (!after.playing() && question == null && !showingOver) {
            showWaiting();
        }
        updateControls();
    }

    private void updateControls() {
        RoomState r = state.roomProperty().get();
        boolean owner = r != null && state.me() != null && r.ownerUserId() == state.me().userId();
        boolean playing = r != null && r.playing();
        UserInfo selected = players.getSelectionModel().getSelectedItem();
        kick.setVisible(owner);
        kick.setManaged(owner);
        kick.setDisable(playing || selected == null || isMe(selected));
        endGame.setVisible(owner && playing);
        endGame.setManaged(owner && playing);
    }

    private void leave() {
        RoomState r = state.roomProperty().get();
        boolean owner = r != null && state.me() != null && r.ownerUserId() == state.me().userId();
        String message = owner ? "你是房主，離開後房間會解散" + (r.playing() ? "，競賽也會中止" : "") + "。確定要離開嗎？"
                : r != null && r.playing() ? "競賽進行中，離開後不能再回到這場競賽。確定要離開嗎？" : null;
        if (message == null || Dialogs.confirm(message)) {
            session.call(MessageType.LEAVE_ROOM, null, LeftRoom.class, left -> { });
        }
    }

    // ---------- 等待與房主設定

    private void showWaiting() {
        stopCountdown();
        question = null;
        RoomState r = state.roomProperty().get();
        boolean owner = r != null && state.me() != null && r.ownerUserId() == state.me().userId();
        if (owner) {
            center.setCenter(setupView);
            return;
        }
        Label waiting = new Label("等待房主開始競賽…");
        waiting.setStyle("-fx-font-size: 18px;");
        Label rules = new Label(rulesText());
        rules.setWrapText(true);
        rules.setStyle("-fx-text-fill: #555;");
        VBox box = new VBox(14, waiting, rules);
        box.setPadding(new Insets(24));
        center.setCenter(box);
    }

    private Parent buildSetup() {
        Label heading = new Label("競賽設定（房主）");
        heading.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        exam.setConverter(new StringConverter<>() {
            @Override
            public String toString(ExamSummary e) {
                if (e == null) {
                    return "";
                }
                return e == ANY_EXAM ? e.title() : e.title() + "（" + e.subject().name() + "，"
                        + (e.questionCount() - e.unansweredCount()) + " 題可用）";
            }

            @Override
            public ExamSummary fromString(String s) {
                throw new UnsupportedOperationException();
            }
        });
        exam.setMaxWidth(Double.MAX_VALUE);
        subject.setConverter(SubjectCells.CONVERTER);
        ToggleGroup order = new ToggleGroup();
        inOrder.setToggleGroup(order);
        random.setToggleGroup(order);
        inOrder.setSelected(true);
        allQuestions.setSelected(true);
        count.setEditable(true);
        count.setPrefWidth(100);
        seconds.getItems().setAll(Protocol.GAME_SECONDS_CHOICES);
        seconds.setValue(20);
        seconds.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer s) {
                return s == null ? "" : s + " 秒";
            }

            @Override
            public Integer fromString(String s) {
                throw new UnsupportedOperationException();
            }
        });
        exam.valueProperty().addListener((obs, a, b) -> {
            // 從「不限考卷」（或清單尚未載入）改選考卷時，回到預設的依序出題
            if ((a == null || a == ANY_EXAM) && b != null && b != ANY_EXAM) {
                inOrder.setSelected(true);
            }
            updateSetupFields();
        });
        order.selectedToggleProperty().addListener((obs, a, b) -> updateSetupFields());
        allQuestions.selectedProperty().addListener((obs, a, b) -> updateSetupFields());

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.addRow(0, new Label("考卷"), exam);
        form.addRow(1, new Label("分類"), subject);
        form.addRow(2, new Label("出題方式"), new HBox(16, inOrder, random));
        form.addRow(3, new Label("題數"), new HBox(12, count, allQuestions));
        form.addRow(4, new Label("每題作答時間"), seconds);
        GridPane.setHgrow(exam, Priority.ALWAYS);

        Button start = new Button("開始競賽");
        start.setDefaultButton(true);
        start.setPrefWidth(140);
        start.setStyle("-fx-font-weight: bold;");
        start.setOnAction(e -> start());
        Button refresh = new Button("重新整理清單");
        refresh.setOnAction(e -> refreshSources());
        Label rules = new Label(rulesText());
        rules.setWrapText(true);
        rules.setStyle("-fx-text-fill: #555;");

        VBox box = new VBox(16, heading, form, new HBox(10, start, refresh), rules);
        box.setPadding(new Insets(20));
        box.setMaxWidth(720);
        updateSetupFields();
        return box;
    }

    private static String rulesText() {
        return "規則：所有玩家（含房主）同時作答；答對得 " + Protocol.GAME_BASE_POINTS + " 分（多選題依學測規則部分給分），"
                + "連續答對從第 2 題起每題加 " + Protocol.GAME_STREAK_STEP + " 分，最多加 " + Protocol.GAME_STREAK_MAX_BONUS
                + " 分；不看作答速度。所有人作答完或時間到就公布答案與排行榜。答錯的題目會加入錯題本。";
    }

    private void updateSetupFields() {
        boolean examChosen = exam.getValue() != null && exam.getValue() != ANY_EXAM;
        subject.setDisable(examChosen);
        inOrder.setDisable(!examChosen);
        if (!examChosen) {
            random.setSelected(true);
        }
        allQuestions.setDisable(random.isSelected());
        count.setDisable(inOrder.isSelected() && allQuestions.isSelected());
    }

    private void refreshSources() {
        session.call(MessageType.LIST_EXAMS, new ListExamsRequest(null), ExamList.class, list -> {
            Long keep = exam.getValue() == null ? null : exam.getValue().id();
            List<ExamSummary> items = new ArrayList<>();
            items.add(ANY_EXAM);
            items.addAll(list.exams().stream().filter(e -> e.questionCount() > e.unansweredCount()).toList());
            exam.getItems().setAll(items);
            exam.setValue(items.stream().filter(e -> keep != null && e.id() == keep).findFirst()
                    .orElse(items.size() > 1 ? items.get(1) : ANY_EXAM));
        });
        session.call(MessageType.LIST_SUBJECTS, null, SubjectList.class, list -> {
            List<SubjectInfo> items = new ArrayList<>();
            items.add(SubjectCells.ALL);
            items.addAll(list.subjects());
            subject.getItems().setAll(items);
            subject.setValue(SubjectCells.ALL);
        });
    }

    private void start() {
        ExamSummary e = exam.getValue();
        boolean examChosen = e != null && e != ANY_EXAM;
        SubjectInfo s = subject.getValue();
        Long subjectId = examChosen || s == null || s == SubjectCells.ALL ? null : s.id();
        boolean isRandom = !examChosen || random.isSelected();
        Integer n = !isRandom && allQuestions.isSelected() ? null : count.getValue();
        session.call(MessageType.START_GAME, new StartGameRequest(examChosen ? e.id() : null, subjectId, isRandom, n,
                seconds.getValue()), GameStarted.class, started -> { });
    }

    // ---------- 競賽訊息

    private void onGame(Message message) {
        try {
            MessageType type = MessageType.fromWire(message.type()).orElseThrow();
            switch (type) {
                case GAME_STARTED -> showStarting(message.payloadAs(GameStarted.class));
                case GAME_QUESTION -> showQuestion(message.payloadAs(GameQuestion.class));
                case GAME_PROGRESS -> {
                    GameProgress p = message.payloadAs(GameProgress.class);
                    if (progressLabel != null && question != null && p.index() == question.index()) {
                        progressLabel.setText("已作答 " + p.answered() + " / " + p.players() + " 人");
                    }
                }
                case QUESTION_RESULT -> showResult(message.payloadAs(QuestionResult.class));
                case GAME_OVER -> showOver(message.payloadAs(GameOver.class));
                default -> {
                }
            }
        } catch (ProtocolException e) {
            Dialogs.error("伺服器訊息格式錯誤：" + e.getMessage());
        }
        updateControls();
    }

    private void showStarting(GameStarted g) {
        showingOver = false;
        question = null;
        Label title = new Label(g.title());
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        Label info = new Label(g.questionCount() + " 題，每題 " + g.secondsPerQuestion() + " 秒，" + g.players().size()
                + " 位玩家。準備好了嗎？");
        info.setStyle("-fx-font-size: 16px;");
        VBox box = new VBox(16, title, info);
        box.setAlignment(Pos.CENTER);
        center.setCenter(box);
    }

    private void showQuestion(GameQuestion q) {
        question = q;
        showingOver = false;
        PracticeQuestion pq = q.question();

        Label progress = new Label("第 " + (q.index() + 1) + " / " + q.total() + " 題");
        progress.setStyle("-fx-font-weight: bold;");
        Label timer = new Label();
        timer.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1565c0;");
        ProgressBar bar = new ProgressBar(1);
        bar.setPrefWidth(220);
        progressLabel = new Label();
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(16, progress, spacer, progressLabel, bar, timer);
        top.setAlignment(Pos.CENTER_LEFT);

        Label type = new Label("［" + QuestionTypes.label(pq.type()) + "題］"
                + (pq.type() == QuestionType.MULTIPLE_CHOICE ? "（可複選，依學測規則部分給分）" : ""));
        type.setStyle("-fx-text-fill: #555;");
        Label content = new Label(pq.content());
        content.setWrapText(true);
        content.setStyle("-fx-font-size: 17px;");

        VBox options = new VBox(10);
        List<javafx.scene.control.ButtonBase> inputs = new ArrayList<>();
        ToggleGroup single = new ToggleGroup();
        if (pq.type() == QuestionType.TRUE_FALSE) {
            for (String label : List.of("對", "錯")) {
                RadioButton b = new RadioButton(label);
                b.setToggleGroup(single);
                b.setUserData(label.equals("對"));
                inputs.add(b);
            }
        } else {
            for (int i = 0; i < pq.options().size(); i++) {
                PracticeOption o = pq.options().get(i);
                String text = ImageViews.optionText(com.exambattle.client.gui.model.ResultText.letter(i), o.content(),
                        o.imageId());
                javafx.scene.control.ButtonBase b;
                if (pq.type() == QuestionType.SINGLE_CHOICE) {
                    RadioButton r = new RadioButton(text);
                    r.setToggleGroup(single);
                    r.setWrapText(true);
                    b = r;
                } else {
                    CheckBox c = new CheckBox(text);
                    c.setWrapText(true);
                    b = c;
                }
                b.setUserData(o.id());
                ImageViews.decorate((javafx.scene.control.Labeled) b, session.images(), o.imageId(), ImageViews.OPTION_WIDTH);
                inputs.add(b);
            }
        }
        options.getChildren().addAll(inputs);

        Button submit = new Button("送出答案");
        submit.setDefaultButton(true);
        submit.setStyle("-fx-font-weight: bold;");
        Label status = new Label();
        status.setStyle("-fx-text-fill: #555;");
        submit.setOnAction(e -> {
            SubmitAnswerRequest request = answer(q, inputs);
            if (request == null) {
                Dialogs.error("請先選擇答案");
                return;
            }
            submit.setDisable(true);
            inputs.forEach(b -> b.setDisable(true));
            status.setText("送出中…");
            session.call(MessageType.SUBMIT_ANSWER, request, AnswerAccepted.class,
                    ok -> status.setText("已送出，等待其他玩家或時間到…"),
                    error -> {
                        status.setText(error);
                        // 逾時等錯誤：維持停用，等伺服器公布結果
                    });
        });

        VBox body = new VBox(14, top, type);
        if (pq.passage() != null || pq.passageImageId() != null) {
            body.getChildren().add(PassageView.create(session.images(), pq.passage(), pq.passageImageId(), 15, true));
        }
        body.getChildren().addAll(ImageViews.withImage(content, session.images(), pq.imageId(), ImageViews.STEM_WIDTH),
                options, new HBox(12, submit, status));
        body.setPadding(new Insets(20));
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        center.setCenter(scroll);

        startCountdown(q.durationMs(), timer, bar, () -> {
            if (!submit.isDisabled()) {
                submit.setDisable(true);
                inputs.forEach(b -> b.setDisable(true));
                status.setText("時間到，等待公布答案…");
            }
        });
    }

    /** 目前選擇的答案；沒有選擇時為 null。 */
    private static SubmitAnswerRequest answer(GameQuestion q, List<javafx.scene.control.ButtonBase> inputs) {
        if (q.question().type() == QuestionType.TRUE_FALSE) {
            for (javafx.scene.control.ButtonBase b : inputs) {
                if (((RadioButton) b).isSelected()) {
                    return new SubmitAnswerRequest(q.gameId(), q.index(), null, (Boolean) b.getUserData());
                }
            }
            return null;
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (javafx.scene.control.ButtonBase b : inputs) {
            boolean selected = b instanceof RadioButton r ? r.isSelected() : ((CheckBox) b).isSelected();
            if (selected) {
                ids.add((Long) b.getUserData());
            }
        }
        return ids.isEmpty() ? null : new SubmitAnswerRequest(q.gameId(), q.index(), List.copyOf(ids), null);
    }

    private void showResult(QuestionResult r) {
        stopCountdown();
        PracticeQuestion pq = question != null && question.index() == r.index() ? question.question() : null;

        Label verdict = new Label(GameText.verdict(r.you()));
        verdict.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: "
                + (r.you().correct() ? "#2e7d32" : r.you().points() > 0 ? "#b26a00" : "#c62828") + ";");
        VBox body = new VBox(12, new Label("第 " + (r.index() + 1) + " / " + r.total() + " 題結果"), verdict);
        if (pq != null) {
            Label content = new Label(pq.content());
            content.setWrapText(true);
            body.getChildren().add(ImageViews.withImage(content, session.images(), pq.imageId(), ImageViews.STEM_WIDTH));
            for (int i = 0; i < pq.options().size(); i++) {
                PracticeOption o = pq.options().get(i);
                boolean correct = r.correctOptionIds().contains(o.id());
                Label line = new Label((correct ? "✔ " : "　 ") + ImageViews.optionText(
                        com.exambattle.client.gui.model.ResultText.letter(i), o.content(), o.imageId()));
                line.setWrapText(true);
                ImageViews.decorate(line, session.images(), o.imageId(), 240);
                if (correct) {
                    line.setStyle("-fx-text-fill: #2e7d32; -fx-font-weight: bold;");
                }
                body.getChildren().add(line);
            }
            body.getChildren().add(new Label("正確答案：" + GameText.correctAnswer(pq, r.correctOptionIds(),
                    r.correctTrueFalse())));
        }
        if (r.explanation() != null && !r.explanation().isBlank()) {
            Label exp = new Label("解說：" + r.explanation());
            exp.setWrapText(true);
            exp.setStyle("-fx-text-fill: #444;");
            body.getChildren().add(exp);
        }
        body.getChildren().addAll(bold("排行榜"), leaderboard(r.leaderboard(), true));
        Label next = new Label(r.index() + 1 < r.total()
                ? "下一題將在 " + Math.round(r.nextInMs() / 1000.0) + " 秒後開始…"
                : "最終排行榜將在 " + Math.round(r.nextInMs() / 1000.0) + " 秒後公布…");
        next.setStyle("-fx-text-fill: #555;");
        body.getChildren().add(next);
        body.setPadding(new Insets(20));
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        center.setCenter(scroll);
    }

    private void showOver(GameOver over) {
        stopCountdown();
        question = null;
        showingOver = true;
        Label title = new Label("競賽結束：" + over.title());
        title.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        Label info = new Label((over.reason() == GameEndReason.ENDED_BY_OWNER ? "房主提前結束，" : "")
                + "共進行 " + over.questionsPlayed() + " / " + over.questionCount() + " 題。"
                + (over.wrongAdded() > 0 ? "你有 " + over.wrongAdded() + " 題沒有全對，已加入錯題本。" : "你全部答對！"));
        info.setWrapText(true);
        Button back = new Button("回到房間");
        back.setOnAction(e -> {
            showingOver = false;
            showWaiting();
        });
        VBox body = new VBox(14, title, info, bold("最終排行榜"), leaderboard(over.leaderboard(), false), back);
        body.setPadding(new Insets(20));
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        center.setCenter(scroll);
    }

    private Node leaderboard(List<LeaderboardEntry> entries, boolean withLast) {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(6);
        List<String> headers = new ArrayList<>(List.of("名次", "玩家", "總分", "答對", "連續答對"));
        if (withLast) {
            headers.add("本題");
        }
        for (int c = 0; c < headers.size(); c++) {
            grid.add(bold(headers.get(c)), c, 0);
        }
        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry e = entries.get(i);
            List<String> cells = new ArrayList<>(List.of(medal(e.rank()), e.player().displayName() + (isMe(e.player()) ? "（你）" : ""),
                    String.valueOf(e.score()), String.valueOf(e.correctCount()), String.valueOf(e.streak())));
            if (withLast) {
                cells.add("+" + e.lastPoints());
            }
            for (int c = 0; c < cells.size(); c++) {
                Label cell = new Label(cells.get(c));
                if (isMe(e.player())) {
                    cell.setStyle("-fx-font-weight: bold; -fx-text-fill: #1565c0;");
                }
                grid.add(cell, c, i + 1);
            }
        }
        grid.setPadding(new Insets(8));
        grid.setStyle("-fx-background-color: white; -fx-border-color: #dde3ea; -fx-border-radius: 4;");
        return grid;
    }

    private static String medal(int rank) {
        return switch (rank) {
            case 1 -> "🥇 1";
            case 2 -> "🥈 2";
            case 3 -> "🥉 3";
            default -> String.valueOf(rank);
        };
    }

    // ---------- 倒數（只用於顯示；是否逾時由伺服器判斷）

    private void startCountdown(long durationMs, Label timer, ProgressBar bar, Runnable onTimeUp) {
        stopCountdown();
        long end = System.currentTimeMillis() + durationMs;
        Runnable tick = () -> {
            long left = end - System.currentTimeMillis();
            timer.setText(GameText.secondsLeft(left) + " 秒");
            bar.setProgress(Math.max(0, (double) left / durationMs));
            if (left <= 0) {
                stopCountdown();
                onTimeUp.run();
            }
        };
        tick.run();
        countdown = new Timeline(new KeyFrame(Duration.millis(100), e -> tick.run()));
        countdown.setCycleCount(Timeline.INDEFINITE);
        countdown.play();
    }

    private void stopCountdown() {
        if (countdown != null) {
            countdown.stop();
            countdown = null;
        }
    }

    private boolean isMe(UserInfo user) {
        return state.me() != null && user != null && state.me().userId() == user.userId();
    }

    private static Label bold(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }
}
