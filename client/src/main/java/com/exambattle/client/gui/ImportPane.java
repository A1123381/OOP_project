package com.exambattle.client.gui;

import com.exambattle.client.gui.model.DraftImage;
import com.exambattle.client.gui.model.ExamDraft;
import com.exambattle.client.gui.model.QuestionTypes;
import com.exambattle.client.importer.AnswerKeyApplier;
import com.exambattle.client.importer.ImportDraftFile;
import com.exambattle.client.importer.ImportDraftFile.ImportDraft;
import com.exambattle.client.importer.ImportGroup;
import com.exambattle.client.importer.ImportItem;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageCodec;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.bank.ExamList;
import com.exambattle.common.protocol.bank.ExamSummary;
import com.exambattle.common.protocol.bank.ImportDone;
import com.exambattle.common.protocol.bank.ImportRequest;
import com.exambattle.common.protocol.bank.ListExamsRequest;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.SubjectInfo;
import com.exambattle.common.protocol.bank.SubjectList;
import com.exambattle.ocr.AnswerKeyParser;
import com.exambattle.ocr.ExtractionException;
import com.exambattle.ocr.ExtractionMethod;
import com.exambattle.ocr.ExtractionResult;
import com.exambattle.ocr.ExtractionService;
import com.exambattle.ocr.OcrConfig;
import com.exambattle.ocr.PageImages;
import com.exambattle.ocr.PageText;
import com.exambattle.ocr.QuestionSplitter;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 「匯入」分頁：擷取（第六階段）→ 校正、設定答案、匯入題庫（第七階段）。
 * <ul>
 *   <li>原始檔只在本機讀取；對照用的頁面圖片即時轉出、只存在記憶體（D-027、D-032）</li>
 *   <li>答案由使用者設定或匯入答案卷，系統不推測（D-030）</li>
 *   <li>匯入題庫為單一請求、單一交易（D-031）</li>
 *   <li>題組（閱讀測驗、克漏字）的文章由同組題目共用，在校正區上方編輯（D-034、D-035）</li>
 * </ul>
 */
final class ImportPane {

    private final GuiSession session;
    private final BorderPane root = new BorderPane();

    // 擷取
    private final Label fileLabel = new Label("尚未選擇檔案");
    private final CheckBox forceOcr = new CheckBox("強制 OCR");
    private final Button choose = new Button("選擇檔案…");
    private final Button start = new Button("開始擷取");
    private final Button cancel = new Button("取消");
    private final ProgressBar progress = new ProgressBar(0);
    private final Label status = new Label();
    private final AtomicBoolean cancelRequested = new AtomicBoolean();
    private boolean running;

    // 校正
    private final Button openDraft = new Button("開啟草稿…");
    private final Button saveDraft = new Button("儲存草稿…");
    private final Button answerKey = new Button("匯入答案卷…");
    private final Button importButton = new Button("匯入題庫…");
    private final Label summary = new Label();
    private final ListView<ImportItem> items = new ListView<>();
    private final QuestionEditor editor;
    private final TextField numberField = new TextField();
    private final Label problem = new Label();
    private final Label warnings = new Label();
    private final VBox editorBox;

    // 題組
    private final Label groupTitle = new Label();
    private final TextArea passage = new TextArea();
    private final Label groupWarnings = new Label();
    private final Button leaveGroup = new Button("移出題組");
    private final Button joinPrevious = new Button("加入上一題的題組");
    private final Button newGroup = new Button("新增題組…");
    private final VBox groupBox;
    private ImageSlot groupImage;

    // 框選圖片（D-043）：在原始頁面上拖曳，放開後交給 framingReceiver
    private final javafx.scene.shape.Rectangle selection = new javafx.scene.shape.Rectangle();
    private TabPane reference;
    private java.util.function.Consumer<DraftImage> framingReceiver;
    private String framingTarget;
    private double dragX;
    private double dragY;

    // 對照
    private final ImageView pageImage = new ImageView();
    private final CheckBox fitWidth = new CheckBox("符合寬度");
    private final Label imageHint = new Label();
    private final Button chooseSource = new Button("選擇原始檔以對照…");
    private final TextArea pageText = new TextArea();
    private final Map<Integer, Image> imageCache = new HashMap<>();

    /** 本次選擇的原始檔（只在記憶體中，不寫入草稿）；從草稿開啟時為 null。 */
    private Path source;
    private String sourceName;
    private List<PageText> pages = List.of();
    private ImportItem current;

    ImportPane(GuiSession session) {
        this.session = session;
        this.editor = new QuestionEditor(session.images());
        editor.setFrameHandler(this::startFraming);

        choose.setOnAction(e -> chooseFile());
        start.setOnAction(e -> startExtraction());
        cancel.setOnAction(e -> {
            cancelRequested.set(true);
            status.setText("取消中，目前這一頁完成後停止…");
        });
        openDraft.setOnAction(e -> openDraft());
        saveDraft.setOnAction(e -> saveDraft());
        answerKey.setOnAction(e -> importAnswerKey());
        importButton.setOnAction(e -> importToBank());
        importButton.setStyle("-fx-font-weight: bold;");
        chooseSource.setOnAction(e -> chooseSourceForComparison());
        progress.setPrefWidth(180);

        Label privacy = new Label("🔒 檔案只在這台電腦上處理，不會上傳；系統不推測答案，答案由你設定或從答案卷匯入。");
        privacy.setStyle("-fx-text-fill: #2e7d32;");
        HBox row1 = new HBox(10, choose, fileLabel, forceOcr, start, cancel, progress, status);
        row1.setAlignment(Pos.CENTER_LEFT);
        HBox row2 = new HBox(10, openDraft, saveDraft, answerKey, importButton, summary);
        row2.setAlignment(Pos.CENTER_LEFT);
        VBox top = new VBox(8, privacy, row1, row2);
        top.setPadding(new Insets(10, 14, 10, 14));
        top.setStyle("-fx-background-color: #f5f8fc;");

        // 左：題目清單
        items.setCellFactory(v -> new ItemCell());
        items.setPlaceholder(new Label("尚無題目，請先擷取檔案或開啟草稿"));
        items.getSelectionModel().selectedItemProperty().addListener((obs, a, b) -> select(b));
        VBox.setVgrow(items, Priority.ALWAYS);
        Button add = new Button("新增題目");
        add.setOnAction(e -> addItem());
        Button remove = new Button("刪除");
        remove.setOnAction(e -> removeItem());
        Button all = new Button("全選");
        all.setOnAction(e -> setAllIncluded(true));
        Button none = new Button("全不選");
        none.setOnAction(e -> setAllIncluded(false));
        Label legend = new Label("✔ 可匯入　○ 未設答案　⚠ 需修正　☁ 已匯入");
        legend.setStyle("-fx-text-fill: #555;");
        VBox left = new VBox(8, bold("題目（勾選要匯入的題目）"), items, new HBox(6, add, remove, all, none), legend);
        left.setPadding(new Insets(12));
        left.setPrefWidth(330);

        // 中：校正編輯區
        numberField.setPrefColumnCount(5);
        numberField.textProperty().addListener((obs, a, b) -> {
            if (current != null && !b.equals(numberText(current))) {
                current.setNumber(b.isBlank() || !b.strip().matches("\\d{1,3}") ? null : Integer.parseInt(b.strip()));
                refreshCurrent();
            }
        });
        editor.setOnChange(() -> {
            if (current != null && !current.imported()) {
                current.setDraft(editor.current());
                refreshCurrent();
            }
        });
        problem.setWrapText(true);
        problem.setStyle("-fx-text-fill: #c62828;");
        warnings.setWrapText(true);
        warnings.setStyle("-fx-text-fill: #b26a00;");

        passage.setWrapText(true);
        passage.setPrefRowCount(8);
        passage.setPromptText("題組文章（同組題目共用；克漏字的空格可寫成【11】）");
        passage.textProperty().addListener((obs, a, b) -> {
            if (current != null && current.group() != null && !b.equals(current.group().passage())) {
                current.group().setPassage(b);
                refreshCurrent();
            }
        });
        groupTitle.setStyle("-fx-font-weight: bold;");
        groupWarnings.setWrapText(true);
        groupWarnings.setStyle("-fx-text-fill: #b26a00;");
        leaveGroup.setOnAction(e -> leaveGroup());
        joinPrevious.setOnAction(e -> joinPreviousGroup());
        newGroup.setOnAction(e -> createGroup());
        groupTitle.setWrapText(true);
        groupImage = new ImageSlot(session.images(), "題組文章", 240);
        groupImage.setFrameHandler(this::startFraming);
        groupImage.setOnChange(() -> {
            if (current != null && current.group() != null) {
                current.group().setImage(groupImage.value());
                refreshCurrent();
            }
        });
        groupBox = new VBox(6, groupTitle, new HBox(8, leaveGroup, joinPrevious, newGroup), passage, groupImage.root(),
                groupWarnings);
        groupBox.setPadding(new Insets(8));
        groupBox.setStyle("-fx-background-color: #f3f6ee; -fx-background-radius: 4;");

        editorBox = new VBox(10, bold("校正"), groupBox, new HBox(8, new Label("題號"), numberField,
                new Label("（用於對應答案卷）")), editor.root(), problem, warnings);
        editorBox.setPadding(new Insets(12));
        ScrollPane editorScroll = new ScrollPane(editorBox);
        editorScroll.setFitToWidth(true);

        // 右：原始頁面與擷取文字
        pageImage.setPreserveRatio(true);
        fitWidth.setSelected(true);
        selection.setFill(javafx.scene.paint.Color.web("#1e88e5", 0.15));
        selection.setStroke(javafx.scene.paint.Color.web("#1e88e5"));
        selection.setStrokeWidth(2);
        selection.setVisible(false);
        selection.setManaged(false);
        javafx.scene.layout.Pane pageHost = new javafx.scene.layout.Pane(pageImage, selection);
        installFraming(pageHost);
        ScrollPane imageScroll = new ScrollPane(pageHost);
        pageImage.fitWidthProperty().bind(javafx.beans.binding.Bindings.when(fitWidth.selectedProperty())
                .then(imageScroll.widthProperty().subtract(20)).otherwise(0));
        imageHint.setWrapText(true);
        imageHint.setStyle("-fx-text-fill: #555;");
        VBox.setVgrow(imageScroll, Priority.ALWAYS);
        VBox imageBox = new VBox(6, new HBox(10, fitWidth, chooseSource), imageHint, imageScroll);
        imageBox.setPadding(new Insets(8));
        pageText.setEditable(false);
        pageText.setWrapText(true);
        reference = new TabPane(new Tab("原始頁面", imageBox), new Tab("擷取文字", pageText));
        reference.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        SplitPane split = new SplitPane(editorScroll, reference);
        split.setOrientation(Orientation.HORIZONTAL);
        split.setDividerPositions(0.5);

        root.setTop(top);
        root.setLeft(left);
        root.setCenter(split);
        select(null);
        updateButtons();
    }

    Parent root() {
        return root;
    }

    // ---------- 擷取（第六階段）

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("選擇考卷檔案");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("考卷（PDF、JPG、PNG）", "*.pdf", "*.jpg", "*.jpeg", "*.png"));
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file != null) {
            selectFile(file.toPath());
        }
    }

    /** 選定要擷取的檔案（只記住路徑，按「開始擷取」才讀取）。 */
    void selectFile(Path file) {
        source = file;
        imageCache.clear();
        fileLabel.setText(file.getFileName().toString());
        updateButtons();
    }

    void startExtraction() {
        if (source == null || running) {
            return;
        }
        if (!items.getItems().isEmpty() && items.getItems().stream().anyMatch(i -> !i.imported())
                && !Dialogs.confirm("重新擷取會取代目前的校正內容（尚未儲存的修改會遺失），確定嗎？")) {
            return;
        }
        Path file = source;
        boolean force = forceOcr.isSelected();
        running = true;
        cancelRequested.set(false);
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        status.setText("處理中…");
        updateButtons();
        Thread worker = new Thread(() -> {
            try {
                ExtractionResult result = new ExtractionService(OcrConfig.locate()).extract(file, force,
                        (page, count, total) -> Platform.runLater(() -> {
                            progress.setProgress((double) count / total);
                            status.setText("第 " + count + " / " + total + " 頁");
                        }),
                        cancelRequested::get);
                Platform.runLater(() -> finish(result));
            } catch (ExtractionException e) {
                Platform.runLater(() -> fail(e.getMessage()));
            } catch (RuntimeException | Error e) {
                Platform.runLater(() -> fail("擷取失敗：" + e));
            }
        }, "import-extraction");
        worker.setDaemon(true);
        worker.start();
    }

    private void finish(ExtractionResult result) {
        running = false;
        progress.setProgress(1);
        long ocrPages = result.pages().stream().filter(p -> p.method() == ExtractionMethod.OCR).count();
        status.setText((result.cancelled() ? "已取消：完成 " : "完成：") + result.pages().size() + " / "
                + result.totalPages() + " 頁（其中 " + ocrPages + " 頁使用 OCR）");
        com.exambattle.ocr.SplitResult split = QuestionSplitter.split(result.pages());
        List<ImportItem> list = ImportItem.fromSplit(split);
        show(result.fileName(), result.pages(), list);
        autoCrop(split, list);
    }

    // ---------- 自動截圖（D-045）：選項看起來是圖片時，依選項記號的座標從原始頁面截圖

    private void autoCrop(com.exambattle.ocr.SplitResult split, List<ImportItem> list) {
        List<Integer> targets = new ArrayList<>();
        for (int i = 0; i < split.questions().size(); i++) {
            if (split.questions().get(i).imageOptions()) {
                targets.add(i);
            }
        }
        if (targets.isEmpty() || source == null) {
            return;
        }
        Path file = source;
        List<PageText> pageList = pages;
        String before = status.getText();
        status.setText(before + "；自動截取圖片選項中…");
        Thread worker = new Thread(() -> {
            Map<Integer, List<com.exambattle.ocr.TextBox>> words = new HashMap<>();
            Map<Integer, BufferedImage> renders = new HashMap<>();
            int done = 0;
            for (int index : targets) {
                com.exambattle.ocr.DraftQuestion q = split.questions().get(index);
                Integer next = index + 1 < split.questions().size() ? split.questions().get(index + 1).number() : null;
                try {
                    int page = q.sourcePage();
                    ExtractionMethod method = pageList.stream().filter(p -> p.page() == page).map(PageText::method)
                            .findFirst().orElse(ExtractionMethod.TEXT_LAYER);
                    List<com.exambattle.ocr.TextBox> pageWords = words.get(page);
                    if (pageWords == null) {
                        pageWords = com.exambattle.ocr.PageWords.of(file, page, method, OcrConfig.locate());
                        words.put(page, pageWords);
                    }
                    Optional<List<com.exambattle.ocr.Region>> regions = com.exambattle.ocr.OptionRegions.locate(
                            pageWords, page, q.number(), next, q.options().size());
                    if (regions.isEmpty()) {
                        continue;
                    }
                    BufferedImage render = renders.get(page);
                    if (render == null) {
                        render = PageImages.render(file, page, PageImages.CROP_DPI);
                        renders.put(page, render);
                    }
                    List<DraftImage> crops = new ArrayList<>();
                    for (com.exambattle.ocr.Region r : regions.get()) {
                        crops.add(com.exambattle.client.gui.model.ImageTools.prepare(r.cropTrimmed(render)));
                    }
                    ImportItem item = list.get(index);
                    done++;
                    Platform.runLater(() -> {
                        List<com.exambattle.client.gui.model.QuestionDraft.Option> options = new ArrayList<>();
                        var draft = item.draft();
                        for (int i = 0; i < draft.options().size(); i++) {
                            var o = draft.options().get(i);
                            options.add(i < crops.size() && o.image() == null ? o.withImage(crops.get(i)) : o);
                        }
                        item.setDraft(draft.withOptions(options));
                        if (current == item) {
                            select(item);
                        }
                        items.refresh();
                        refreshSummary();
                    });
                } catch (ExtractionException | RuntimeException e) {
                    // 截不到就留給使用者框選或自備圖片
                }
            }
            int cropped = done;
            Platform.runLater(() -> status.setText(before + "；自動截取 " + cropped + " / " + targets.size()
                    + " 題的圖片選項，請對照原始頁面確認"));
        }, "auto-crop");
        worker.setDaemon(true);
        worker.start();
    }

    // ---------- 框選圖片（D-043）

    /** ImageSlot 要求框選：切到原始頁面，拖曳完成後把截圖交給 receiver。 */
    private void startFraming(String target, java.util.function.Consumer<DraftImage> receiver) {
        if (source == null) {
            Dialogs.error("框選需要原始檔。請先按「選擇原始檔以對照…」選擇原始檔（只在本機讀取）。");
            return;
        }
        framingTarget = target;
        framingReceiver = receiver;
        reference.getSelectionModel().select(0);
        imageHint.setText("請在下方頁面上拖曳，框出「" + target + "」的圖片範圍（按 Esc 取消）。");
        pageImage.setStyle("-fx-cursor: crosshair;");
    }

    private void installFraming(javafx.scene.layout.Pane host) {
        host.setOnMousePressed(e -> {
            if (framingReceiver == null) {
                return;
            }
            dragX = e.getX();
            dragY = e.getY();
            selection.setX(dragX);
            selection.setY(dragY);
            selection.setWidth(0);
            selection.setHeight(0);
            selection.setVisible(true);
        });
        host.setOnMouseDragged(e -> {
            if (framingReceiver == null) {
                return;
            }
            selection.setX(Math.min(dragX, e.getX()));
            selection.setY(Math.min(dragY, e.getY()));
            selection.setWidth(Math.abs(e.getX() - dragX));
            selection.setHeight(Math.abs(e.getY() - dragY));
        });
        host.setOnMouseReleased(e -> {
            if (framingReceiver == null) {
                return;
            }
            javafx.geometry.Bounds view = pageImage.getBoundsInParent();
            if (selection.getWidth() < 5 || selection.getHeight() < 5 || view.getWidth() <= 0) {
                return;
            }
            com.exambattle.ocr.Region region = new com.exambattle.ocr.Region(current == null ? 1 : current.sourcePage(),
                    (selection.getX() - view.getMinX()) / view.getWidth(), (selection.getY() - view.getMinY()) / view.getHeight(),
                    selection.getWidth() / view.getWidth(), selection.getHeight() / view.getHeight());
            java.util.function.Consumer<DraftImage> receiver = framingReceiver;
            endFraming();
            cropAsync(region, receiver);
        });
        host.setFocusTraversable(true);
        host.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE && framingReceiver != null) {
                endFraming();
            }
        });
    }

    private void endFraming() {
        framingReceiver = null;
        selection.setVisible(false);
        pageImage.setStyle("");
        showReference(current);
    }

    private void cropAsync(com.exambattle.ocr.Region region, java.util.function.Consumer<DraftImage> receiver) {
        Path file = source;
        String target = framingTarget;
        Thread worker = new Thread(() -> {
            try {
                BufferedImage render = PageImages.render(file, region.page(), PageImages.CROP_DPI);
                DraftImage image = com.exambattle.client.gui.model.ImageTools.prepare(region.cropTrimmed(render));
                Platform.runLater(() -> receiver.accept(image));
            } catch (ExtractionException e) {
                Platform.runLater(() -> Dialogs.error("無法截取「" + target + "」的圖片：" + e.getMessage()));
            }
        }, "frame-crop");
        worker.setDaemon(true);
        worker.start();
    }

    private void fail(String message) {
        running = false;
        progress.setProgress(0);
        status.setText("失敗");
        updateButtons();
        Dialogs.error(message);
    }

    private void show(String name, List<PageText> pageList, List<ImportItem> list) {
        sourceName = name;
        pages = List.copyOf(pageList);
        items.getItems().setAll(list);
        refreshSummary();
        updateButtons();
        if (!list.isEmpty()) {
            items.getSelectionModel().select(0);
        } else {
            select(null);
        }
    }

    // ---------- 題目清單與校正

    private final class ItemCell extends ListCell<ImportItem> {
        private final CheckBox box = new CheckBox();
        private final Label text = new Label();
        private final HBox graphic = new HBox(6, box, text);

        ItemCell() {
            graphic.setAlignment(Pos.CENTER_LEFT);
            box.setOnAction(e -> {
                if (getItem() != null) {
                    getItem().setInclude(box.isSelected());
                    refreshSummary();
                }
            });
        }

        @Override
        protected void updateItem(ImportItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            box.setSelected(item.include());
            box.setDisable(item.imported());
            String type = item.draft().type() == null ? "題型？" : QuestionTypes.label(item.draft().type());
            String first = item.draft().content().lines().findFirst().orElse("");
            if (first.length() > 24) {
                first = first.substring(0, 24) + "…";
            }
            String tags = (item.group() == null ? "" : "〔" + item.group().label() + "〕")
                    + (item.unsupported() ? "〔非選擇題〕" : "");
            text.setText(statusMark(item) + " " + numberText(item) + ". " + tags + "[" + type + "] " + first);
            text.setStyle(switch (item.status()) {
                case NEEDS_FIX -> "-fx-text-fill: #c62828;";
                case NO_ANSWER -> "-fx-text-fill: #b26a00;";
                case IMPORTED -> "-fx-text-fill: #888;";
                case READY -> "";
            });
            setGraphic(graphic);
            setText(null);
        }
    }

    private static String statusMark(ImportItem item) {
        return switch (item.status()) {
            case READY -> "✔";
            case NO_ANSWER -> "○";
            case NEEDS_FIX -> "⚠";
            case IMPORTED -> "☁";
        };
    }

    private static String numberText(ImportItem item) {
        return item.number() == null ? "?" : String.valueOf(item.number());
    }

    private void select(ImportItem item) {
        current = null;
        if (item == null) {
            editorBox.setDisable(true);
            numberField.setText("");
            problem.setText("");
            warnings.setText("");
            showGroup(null);
            showReference(null);
            return;
        }
        numberField.setText(numberText(item).equals("?") ? "" : numberText(item));
        editor.load(item.draft());
        editorBox.setDisable(item.imported());
        showGroup(item);
        current = item;
        refreshCurrent();
        showReference(item);
    }

    /** 編輯後更新清單、驗證訊息與摘要。 */
    private void refreshCurrent() {
        if (current == null) {
            return;
        }
        if (current.imported()) {
            problem.setText("這題已匯入題庫，不能再修改。");
        } else {
            problem.setText(current.problem().map(p -> "需修正：" + p)
                    .orElse(current.draft().answerSet() ? "" : "尚未設定正確答案（仍可匯入，但不會用於練習）"));
        }
        warnings.setText(current.warnings().isEmpty() ? "" : "擷取時的提醒：" + String.join("；", current.warnings()));
        items.refresh();
        refreshSummary();
    }

    // ---------- 題組

    /** 顯示所選題目的題組文章；不屬於題組時只顯示「新增題組」。 */
    private void showGroup(ImportItem item) {
        ImportGroup group = item == null ? null : item.group();
        boolean inGroup = group != null;
        passage.setVisible(inGroup);
        passage.setManaged(inGroup);
        leaveGroup.setVisible(inGroup);
        leaveGroup.setManaged(inGroup);
        joinPrevious.setDisable(previousGroup(item) == null || previousGroup(item) == group);
        groupImage.root().setVisible(inGroup);
        groupImage.root().setManaged(inGroup);
        groupImage.load(inGroup ? group.image() : null);
        if (!inGroup) {
            groupTitle.setText("不屬於題組");
            groupWarnings.setText("");
            return;
        }
        long size = items.getItems().stream().filter(i -> i.group() == group).count();
        groupTitle.setText(group.label() + "（同組 " + size + " 題共用這篇文章）");
        passage.setText(group.passage());
        groupWarnings.setText(group.warnings().isEmpty() ? "" : "擷取時的提醒：" + String.join("；", group.warnings()));
    }

    /** 清單中上一題所屬的題組。 */
    private ImportGroup previousGroup(ImportItem item) {
        int index = item == null ? -1 : items.getItems().indexOf(item);
        return index <= 0 ? null : items.getItems().get(index - 1).group();
    }

    private void leaveGroup() {
        if (current != null && current.group() != null) {
            current.setGroup(null);
            showGroup(current);
            refreshCurrent();
        }
    }

    private void joinPreviousGroup() {
        ImportGroup group = previousGroup(current);
        if (current != null && group != null) {
            current.setGroup(group);
            showGroup(current);
            refreshCurrent();
        }
    }

    /** 選擇題號範圍並貼上文章，把範圍內（尚未匯入）的題目放進新的題組。 */
    private void createGroup() {
        Dialog<ImportGroup> dialog = new Dialog<>();
        dialog.setTitle("新增題組");
        dialog.setHeaderText("範圍內的題目會共用這篇文章（原本屬於其他題組的題目會移過來）。");
        String number = current == null || current.number() == null ? "" : String.valueOf(current.number());
        TextField from = new TextField(number);
        TextField to = new TextField(number);
        from.setPrefColumnCount(4);
        to.setPrefColumnCount(4);
        TextArea text = new TextArea(current != null && current.group() != null ? current.group().passage() : "");
        text.setWrapText(true);
        text.setPrefRowCount(10);
        text.setPromptText("貼上文章；克漏字的空格可寫成【11】");
        VBox content = new VBox(8, new HBox(8, new Label("第"), from, new Label("題 至 第"), to, new Label("題")), text);
        content.setPrefWidth(560);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e -> {
            Optional<String> error = groupRangeError(from.getText(), to.getText());
            if (error.isEmpty()) {
                error = ImportGroup.manual(text.getText(), null, null, 1).problem();
            }
            if (error.isPresent()) {
                Dialogs.error(error.get());
                e.consume();
            }
        });
        dialog.setResultConverter(b -> b != ButtonType.OK ? null : ImportGroup.manual(text.getText(),
                Integer.parseInt(from.getText().strip()), Integer.parseInt(to.getText().strip()),
                current == null ? 1 : current.sourcePage()));
        dialog.showAndWait().ifPresent(group -> {
            int first = Integer.parseInt(from.getText().strip());
            int last = Integer.parseInt(to.getText().strip());
            items.getItems().stream()
                    .filter(i -> !i.imported() && i.number() != null && i.number() >= first && i.number() <= last)
                    .forEach(i -> i.setGroup(group));
            if (current != null) {
                select(current);
            }
            items.refresh();
            refreshSummary();
        });
    }

    private Optional<String> groupRangeError(String from, String to) {
        if (!from.strip().matches("\\d{1,3}") || !to.strip().matches("\\d{1,3}")) {
            return Optional.of("請輸入題號範圍");
        }
        int first = Integer.parseInt(from.strip());
        int last = Integer.parseInt(to.strip());
        if (first > last) {
            return Optional.of("起始題號不可大於結束題號");
        }
        boolean any = items.getItems().stream()
                .anyMatch(i -> !i.imported() && i.number() != null && i.number() >= first && i.number() <= last);
        return any ? Optional.empty() : Optional.of("範圍內沒有尚未匯入的題目");
    }

    private void refreshSummary() {
        List<ImportItem> selected = items.getItems().stream().filter(ImportItem::selected).toList();
        long noAnswer = selected.stream().filter(i -> i.status() == ImportItem.Status.NO_ANSWER).count();
        long needsFix = selected.stream().filter(i -> i.status() == ImportItem.Status.NEEDS_FIX).count();
        long imported = items.getItems().stream().filter(ImportItem::imported).count();
        summary.setText(items.getItems().isEmpty() ? "" : "共 " + items.getItems().size() + " 題，勾選 " + selected.size()
                + " 題（" + noAnswer + " 題未設答案、" + needsFix + " 題需修正）" + (imported > 0 ? "，已匯入 " + imported + " 題" : ""));
        updateButtons();
    }

    private void addItem() {
        int maxNumber = items.getItems().stream().map(ImportItem::number).filter(n -> n != null)
                .mapToInt(Integer::intValue).max().orElse(0);
        int page = current == null ? 1 : current.sourcePage();
        ImportItem item = ImportItem.blank(maxNumber + 1, page);
        int index = current == null ? items.getItems().size() : items.getItems().indexOf(current) + 1;
        items.getItems().add(index, item);
        items.getSelectionModel().select(item);
        refreshSummary();
    }

    private void removeItem() {
        ImportItem item = items.getSelectionModel().getSelectedItem();
        if (item != null && Dialogs.confirm("確定要刪除這題草稿嗎？")) {
            items.getItems().remove(item);
            refreshSummary();
        }
    }

    private void setAllIncluded(boolean include) {
        items.getItems().stream().filter(i -> !i.imported()).forEach(i -> i.setInclude(include));
        items.refresh();
        refreshSummary();
    }

    // ---------- 原始頁面對照（只在本機、只在記憶體）

    private void showReference(ImportItem item) {
        int page = item == null ? 1 : item.sourcePage();
        pageText.setText(pages.stream().filter(p -> p.page() == page).map(PageText::text).findFirst().orElse(""));
        chooseSource.setVisible(source == null && sourceName != null);
        if (source == null) {
            pageImage.setImage(null);
            imageHint.setText(sourceName == null ? "" : "從草稿開啟時沒有原始檔可以對照；可以按「選擇原始檔以對照」（只在本機讀取，不會上傳或寫入草稿）。");
            return;
        }
        imageHint.setText("第 " + page + " 頁（" + source.getFileName() + "，本機即時轉出）");
        Image cached = imageCache.get(page);
        if (cached != null) {
            pageImage.setImage(cached);
            return;
        }
        pageImage.setImage(null);
        Path file = source;
        Thread loader = new Thread(() -> {
            try {
                Image image = toFxImage(PageImages.render(file, page, PageImages.DISPLAY_DPI));
                Platform.runLater(() -> {
                    if (file.equals(source)) {
                        imageCache.put(page, image);
                        if (current == null || current.sourcePage() == page) {
                            pageImage.setImage(image);
                        }
                    }
                });
            } catch (ExtractionException e) {
                Platform.runLater(() -> imageHint.setText("無法顯示原始頁面：" + e.getMessage()));
            }
        }, "page-image");
        loader.setDaemon(true);
        loader.start();
    }

    private void chooseSourceForComparison() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("選擇原始檔（只在本機讀取，用來對照）");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("考卷（PDF、JPG、PNG）", "*.pdf", "*.jpg", "*.jpeg", "*.png"));
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        if (sourceName != null && !file.getName().equals(sourceName)
                && !Dialogs.confirm("選擇的檔案「" + file.getName() + "」與草稿記錄的「" + sourceName + "」不同，仍要使用嗎？")) {
            return;
        }
        source = file.toPath();
        imageCache.clear();
        fileLabel.setText(file.getName());
        showReference(current);
        updateButtons();
    }

    /** BufferedImage 轉成 JavaFX Image（不依賴 javafx-swing）。 */
    private static Image toFxImage(BufferedImage image) {
        WritableImage fx = new WritableImage(image.getWidth(), image.getHeight());
        PixelWriter writer = fx.getPixelWriter();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                writer.setArgb(x, y, image.getRGB(x, y));
            }
        }
        return fx;
    }

    // ---------- 答案卷

    private void importAnswerKey() {
        Dialog<AnswerKeyParser.Result> dialog = new Dialog<>();
        dialog.setTitle("匯入答案卷");
        dialog.setHeaderText("貼上答案（例如：1.B 2.AC 3.○ 4.×），或從答案卷檔案擷取。依題號套用，套用後仍可修改。");
        TextArea text = new TextArea();
        text.setPrefRowCount(8);
        text.setWrapText(true);
        text.setPromptText("1.B 2.AC 3.○ 4.×\n或\n題號 1 2 3\n答案 B C A");
        TextArea preview = new TextArea();
        preview.setEditable(false);
        preview.setPrefRowCount(8);
        Button fromFile = new Button("從答案卷檔案擷取…");
        Label fileStatus = new Label();
        fromFile.setOnAction(e -> extractAnswerSheet(fromFile, fileStatus, text));
        text.textProperty().addListener((obs, a, b) -> preview.setText(describe(AnswerKeyParser.parse(b))));
        VBox content = new VBox(8, new HBox(10, fromFile, fileStatus), text, bold("解析結果預覽"), preview);
        content.setPrefWidth(560);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("套用");
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e -> {
            if (AnswerKeyParser.parse(text.getText()).answers().isEmpty()) {
                Dialogs.error("沒有可以套用的答案");
                e.consume();
            }
        });
        dialog.setResultConverter(b -> b == ButtonType.OK ? AnswerKeyParser.parse(text.getText()) : null);
        dialog.showAndWait().ifPresent(result -> {
            AnswerKeyApplier.Summary s = AnswerKeyApplier.apply(items.getItems(), result.answers());
            if (current != null) {
                select(current);
            }
            items.refresh();
            refreshSummary();
            StringBuilder message = new StringBuilder("已套用 " + s.applied() + " 題的答案。");
            if (!result.nonChoice().isEmpty()) {
                message.append("\n答案卷標為「／」的非選擇題（略過）：").append(result.nonChoice());
            }
            if (!s.notFound().isEmpty()) {
                message.append("\n找不到對應題號：").append(s.notFound());
            }
            if (!s.problems().isEmpty()) {
                message.append("\n未套用：\n・").append(String.join("\n・", s.problems()));
            }
            Dialogs.info(message.toString());
        });
    }

    private void extractAnswerSheet(Button button, Label fileStatus, TextArea target) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("選擇答案卷（只在本機處理）");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("答案卷（PDF、JPG、PNG）", "*.pdf", "*.jpg", "*.jpeg", "*.png"));
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file != null) {
            extractAnswerSheet(file.toPath(), button, fileStatus, target);
        }
    }

    /** 以第六階段的擷取流程讀出答案卷文字，填入文字框供確認與修改。 */
    void extractAnswerSheet(Path file, Button button, Label fileStatus, TextArea target) {
        button.setDisable(true);
        fileStatus.setText("擷取中…");
        Thread worker = new Thread(() -> {
            try {
                ExtractionResult result = new ExtractionService(OcrConfig.locate())
                        .extract(file, false, (p, d, t) -> { }, () -> false);
                String text = String.join("\n", result.pages().stream().map(PageText::text).toList());
                Platform.runLater(() -> {
                    target.setText(text);
                    fileStatus.setText("已擷取 " + file.getFileName() + "，請確認下方預覽");
                    button.setDisable(false);
                });
            } catch (ExtractionException e) {
                Platform.runLater(() -> {
                    fileStatus.setText("");
                    button.setDisable(false);
                    Dialogs.error(e.getMessage());
                });
            }
        }, "answer-sheet");
        worker.setDaemon(true);
        worker.start();
    }

    static String describe(AnswerKeyParser.Result result) {
        StringBuilder text = new StringBuilder();
        result.answers().forEach((n, key) -> text.append(n).append(" → ").append(key.token()).append('\n'));
        if (!result.nonChoice().isEmpty()) {
            text.append("\n非選擇題（略過）：").append(result.nonChoice()).append('\n');
        }
        if (!result.warnings().isEmpty()) {
            text.append("\n⚠ ").append(String.join("\n⚠ ", result.warnings()));
        }
        return text.toString().strip();
    }

    // ---------- 匯入題庫

    private void importToBank() {
        List<ImportItem> selected = items.getItems().stream().filter(ImportItem::selected).toList();
        if (selected.isEmpty()) {
            Dialogs.error("請先勾選要匯入的題目");
            return;
        }
        Optional<ImportItem> broken = selected.stream().filter(i -> i.status() == ImportItem.Status.NEEDS_FIX).findFirst();
        if (broken.isPresent()) {
            items.getSelectionModel().select(broken.get());
            items.scrollTo(broken.get());
            Dialogs.error("勾選的題目中有需要修正的題目（已幫你選取第一題）：\n" + broken.get().problem().orElse(""));
            return;
        }
        if (selected.size() > Protocol.MAX_IMPORT_QUESTIONS) {
            Dialogs.error("一次最多匯入 " + Protocol.MAX_IMPORT_QUESTIONS + " 題，請分批勾選匯入");
            return;
        }
        long noAnswer = selected.stream().filter(i -> i.status() == ImportItem.Status.NO_ANSWER).count();
        if (noAnswer > 0 && !Dialogs.confirm("有 " + noAnswer + " 題尚未設定正確答案，匯入後不會用於練習。仍要匯入嗎？")) {
            return;
        }
        // 圖片先逐張上傳（D-044）；相同內容的圖片伺服器只存一份，重新上傳不會重複
        List<DraftImage> images = ImportItem.localImages(selected);
        if (!images.isEmpty()) {
            status.setText("上傳圖片 0 / " + images.size() + "…");
        }
        ImageUploader.upload(session, images, (doneCount, total) -> status.setText("上傳圖片 " + doneCount + " / " + total + "…"),
                ids -> {
                    ImportItem.applyUploaded(selected, ids);
                    if (!images.isEmpty()) {
                        status.setText("已上傳 " + images.size() + " 張圖片");
                    }
                    session.call(MessageType.LIST_SUBJECTS, null, SubjectList.class, subjects ->
                            session.call(MessageType.LIST_EXAMS, new ListExamsRequest(null), ExamList.class, exams -> {
                                long me = session.state().me().userId();
                                List<ExamSummary> mine = exams.exams().stream().filter(e -> e.owner().userId() == me).toList();
                                showImportDialog(selected, subjects.subjects(), mine);
                            }));
                }, Dialogs::error);
    }

    private void showImportDialog(List<ImportItem> selected, List<SubjectInfo> subjects, List<ExamSummary> mine) {
        Dialog<ImportRequest> dialog = new Dialog<>();
        dialog.setTitle("匯入題庫");
        dialog.setHeaderText("匯入 " + selected.size() + " 題。全部題目一次匯入，任何錯誤都會整批取消。");
        ToggleGroup target = new ToggleGroup();
        RadioButton toNew = new RadioButton("建立新考卷");
        RadioButton toExisting = new RadioButton("加到我的考卷最後");
        toNew.setToggleGroup(target);
        toExisting.setToggleGroup(target);
        toNew.setSelected(true);
        toExisting.setDisable(mine.isEmpty());

        TextField title = new TextField(sourceName == null ? "" : sourceName.replaceAll("\\.[^.]+$", ""));
        CheckBox makePublic = new CheckBox("公開（所有人都能瀏覽、練習、競賽；不勾選為私人）");
        ComboBox<SubjectInfo> subject = new ComboBox<>();
        subject.getItems().setAll(subjects);
        subject.setConverter(SubjectCells.CONVERTER);
        TextField year = new TextField();
        year.setPromptText("例如 2024");
        TextField type = new TextField();
        type.setPromptText("例如 期中考");
        ComboBox<ExamSummary> existing = new ComboBox<>();
        existing.getItems().setAll(mine);
        existing.setConverter(new StringConverter<>() {
            @Override
            public String toString(ExamSummary e) {
                return e == null ? "" : e.title() + "（" + e.subject().name() + "，" + e.questionCount() + " 題）";
            }

            @Override
            public ExamSummary fromString(String s) {
                throw new UnsupportedOperationException();
            }
        });
        if (!mine.isEmpty()) {
            existing.setValue(mine.get(0));
        }

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.addRow(0, toNew);
        form.addRow(1, new Label("　考卷名稱"), title);
        form.addRow(2, new Label("　分類"), subject);
        form.addRow(3, new Label("　考試年份"), year);
        form.addRow(4, new Label("　考試類型"), type);
        form.add(makePublic, 1, 5);
        form.addRow(6, toExisting);
        form.addRow(7, new Label("　考卷"), existing);
        for (javafx.scene.Node n : List.of(title, subject, year, type, makePublic)) {
            n.disableProperty().bind(toExisting.selectedProperty());
        }
        existing.disableProperty().bind(toNew.selectedProperty());
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).setText("匯入");

        ImportItem.RequestParts questions = ImportItem.requestParts(selected);
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e -> {
            Optional<String> error = Optional.empty();
            if (toNew.isSelected()) {
                error = new ExamDraft(title.getText(), subject.getValue(), year.getText(), type.getText()).validate();
            } else if (existing.getValue() == null) {
                error = Optional.of("請選擇考卷");
            }
            if (error.isEmpty()) {
                ImportRequest request = buildRequest(toNew.isSelected(), title, subject, year, type, makePublic, existing,
                        questions);
                int length = MessageCodec.encode(Message.of(MessageType.IMPORT_QUESTIONS, "c-0", request)).length();
                if (length > Protocol.MAX_LINE_CHARS - 100) {
                    error = Optional.of("這次匯入的內容太大，請減少勾選的題目分批匯入");
                }
            }
            if (error.isPresent()) {
                Dialogs.error(error.get());
                e.consume();
            }
        });
        dialog.setResultConverter(b -> b == ButtonType.OK
                ? buildRequest(toNew.isSelected(), title, subject, year, type, makePublic, existing, questions) : null);
        dialog.showAndWait().ifPresent(request ->
                session.call(MessageType.IMPORT_QUESTIONS, request, ImportDone.class, done -> {
                    selected.forEach(ImportItem::markImported);
                    if (current != null) {
                        select(current);
                    }
                    items.refresh();
                    refreshSummary();
                    Dialogs.info("已匯入 " + done.importedCount() + " 題到「" + done.exam().title() + "」（共 "
                            + done.exam().questionCount() + " 題）。\n可以到「題庫」分頁查看，或到「練習」分頁練習。"
                            + "\n建議儲存草稿，保留已匯入的標記。");
                }));
    }

    private static ImportRequest buildRequest(boolean toNew, TextField title, ComboBox<SubjectInfo> subject,
                                              TextField year, TextField type, CheckBox makePublic,
                                              ComboBox<ExamSummary> existing, ImportItem.RequestParts parts) {
        if (toNew) {
            return new ImportRequest(null,
                    new ExamDraft(title.getText(), subject.getValue(), year.getText(), type.getText(),
                            makePublic.isSelected() ? com.exambattle.common.protocol.bank.Visibility.PUBLIC
                                    : com.exambattle.common.protocol.bank.Visibility.PRIVATE).toRequest(null),
                    parts.questions(), parts.groups(), parts.groupIndexes(), parts.groupImageIds());
        }
        return new ImportRequest(existing.getValue().id(), null, parts.questions(), parts.groups(), parts.groupIndexes(),
                parts.groupImageIds());
    }

    // ---------- 草稿

    private void saveDraft() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("儲存匯入草稿");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("匯入草稿", "*." + ImportDraftFile.EXTENSION));
        String base = sourceName == null ? "draft" : sourceName.replaceAll("\\.[^.]+$", "");
        chooser.setInitialFileName(base + "." + ImportDraftFile.EXTENSION);
        File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            ImportDraftFile.save(file.toPath(),
                    ImportDraftFile.create(sourceName == null ? "draft" : sourceName, pages, items.getItems()));
            status.setText("草稿已儲存：" + file.getName());
        } catch (IOException e) {
            Dialogs.error("無法儲存草稿：" + e.getMessage());
        }
    }

    private void openDraft() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("開啟匯入草稿");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("匯入草稿", "*." + ImportDraftFile.EXTENSION, "*.json"));
        File file = chooser.showOpenDialog(root.getScene().getWindow());
        if (file == null) {
            return;
        }
        openDraft(file.toPath());
    }

    void openDraft(Path file) {
        try {
            ImportDraft draft = ImportDraftFile.load(file);
            source = null; // 草稿不含原始檔路徑；需要對照時再選一次
            imageCache.clear();
            fileLabel.setText(draft.fileName() + "（草稿）");
            status.setText("已開啟草稿：" + file.getFileName());
            show(draft.fileName(), draft.pages(), ImportDraftFile.items(draft));
        } catch (IOException e) {
            Dialogs.error(e.getMessage());
        }
    }

    private void updateButtons() {
        choose.setDisable(running);
        start.setDisable(running || source == null);
        cancel.setDisable(!running);
        openDraft.setDisable(running);
        forceOcr.setDisable(running);
        boolean empty = items.getItems().isEmpty();
        saveDraft.setDisable(running || empty);
        answerKey.setDisable(running || empty);
        importButton.setDisable(running || items.getItems().stream().noneMatch(ImportItem::selected));
    }

    private static Label bold(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-weight: bold;");
        return label;
    }
}
