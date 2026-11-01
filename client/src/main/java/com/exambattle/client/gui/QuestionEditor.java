package com.exambattle.client.gui;

import com.exambattle.client.gui.model.QuestionDraft;
import com.exambattle.client.gui.model.QuestionTypes;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.bank.QuestionType;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;

/**
 * 可嵌入的題目編輯區：題型、題目、選項與正確答案、解說。
 * 題庫的「新增／編輯題目」對話框與匯入的校正畫面共用（見 DECISIONS.md D-030）。
 * 單選題以圓形按鈕標示唯一正確答案，多選題以核取方塊標示；是非題選「對／錯／未設定」。
 */
final class QuestionEditor {

    private final VBox root;
    private final ComboBox<QuestionType> type = new ComboBox<>();
    private final TextArea content = new TextArea();
    private final TextArea explanation = new TextArea();
    private final VBox optionRows = new VBox(6);
    private final Button addOption = new Button("新增選項");
    private final VBox optionsBox;
    private final RadioButton tfTrue = new RadioButton("對");
    private final RadioButton tfFalse = new RadioButton("錯");
    private final RadioButton tfUnset = new RadioButton("未設定");
    private final HBox tfBox;
    private final List<OptionRow> rows = new ArrayList<>();
    private ToggleGroup singleGroup = new ToggleGroup();
    private Runnable onChange = () -> { };
    private boolean loading;

    /** 一列選項：內容與「正確」標記（單選為 RadioButton，多選為 CheckBox）。 */
    private record OptionRow(HBox node, TextField text, ButtonBase mark) {
        boolean correct() {
            return mark instanceof Toggle t ? t.isSelected() : ((CheckBox) mark).isSelected();
        }
    }

    QuestionEditor() {
        type.getItems().setAll(QuestionType.values());
        type.setPromptText("請選擇題型");
        type.setConverter(new StringConverter<>() {
            @Override
            public String toString(QuestionType t) {
                return t == null ? "" : QuestionTypes.label(t) + "題";
            }

            @Override
            public QuestionType fromString(String s) {
                throw new UnsupportedOperationException();
            }
        });
        content.setPromptText("題目內容");
        content.setWrapText(true);
        content.setPrefRowCount(4);
        explanation.setPromptText("詳細解說（選填）");
        explanation.setWrapText(true);
        explanation.setPrefRowCount(2);

        addOption.setOnAction(e -> {
            addRow("", false);
            changed();
        });
        optionsBox = new VBox(6, new Label("選項（標示正確答案）"), optionRows, addOption);

        ToggleGroup tf = new ToggleGroup();
        tfTrue.setToggleGroup(tf);
        tfFalse.setToggleGroup(tf);
        tfUnset.setToggleGroup(tf);
        tf.selectedToggleProperty().addListener((obs, a, b) -> changed());
        tfBox = new HBox(16, new Label("正確答案"), tfTrue, tfFalse, tfUnset);

        type.valueProperty().addListener((obs, before, after) -> {
            if (!loading) {
                switchType(before, after);
                changed();
            }
        });
        content.textProperty().addListener((obs, a, b) -> changed());
        explanation.textProperty().addListener((obs, a, b) -> changed());

        root = new VBox(10, new HBox(10, new Label("題型"), type), new Label("題目"), content,
                optionsBox, tfBox, new Label("解說"), explanation);
    }

    Parent root() {
        return root;
    }

    /** 每次內容變動時呼叫（載入題目時不會呼叫）。 */
    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    void setEditable(boolean editable) {
        root.setDisable(!editable);
    }

    /** 載入題目到編輯區。 */
    void load(QuestionDraft draft) {
        loading = true;
        try {
            type.setValue(draft.type());
            content.setText(draft.content());
            explanation.setText(draft.explanation() == null ? "" : draft.explanation());
            rows.clear();
            optionRows.getChildren().clear();
            singleGroup = new ToggleGroup();
            for (QuestionDraft.Option option : draft.options()) {
                addRow(option.content(), option.correct());
            }
            Boolean answer = draft.trueFalseAnswer();
            (answer == null ? tfUnset : answer ? tfTrue : tfFalse).setSelected(true);
            refreshVisibility();
        } finally {
            loading = false;
        }
    }

    /** 目前編輯中的內容。 */
    QuestionDraft current() {
        List<QuestionDraft.Option> options = rows.stream()
                .map(r -> new QuestionDraft.Option(r.text().getText(), r.correct()))
                .toList();
        Boolean tf = tfTrue.isSelected() ? Boolean.TRUE : tfFalse.isSelected() ? Boolean.FALSE : null;
        return new QuestionDraft(type.getValue(), content.getText(), explanation.getText(), options, tf);
    }

    private void changed() {
        if (!loading) {
            onChange.run();
        }
    }

    private void addRow(String text, boolean correct) {
        if (rows.size() >= Protocol.MAX_OPTIONS) {
            Dialogs.error("最多 " + Protocol.MAX_OPTIONS + " 個選項");
            return;
        }
        TextField field = new TextField(text);
        HBox.setHgrow(field, Priority.ALWAYS);
        ButtonBase mark = newMark(correct);
        Button remove = new Button("刪除");
        HBox node = new HBox(8, mark, field, remove);
        OptionRow row = new OptionRow(node, field, mark);
        remove.setOnAction(e -> {
            rows.remove(row);
            optionRows.getChildren().remove(node);
            relabel();
            changed();
        });
        field.textProperty().addListener((obs, a, b) -> changed());
        rows.add(row);
        optionRows.getChildren().add(node);
        relabel();
    }

    private ButtonBase newMark(boolean correct) {
        if (type.getValue() == QuestionType.MULTIPLE_CHOICE) {
            CheckBox box = new CheckBox();
            box.setSelected(correct);
            box.selectedProperty().addListener((obs, a, b) -> changed());
            return box;
        }
        RadioButton radio = new RadioButton();
        radio.setToggleGroup(singleGroup);
        radio.setSelected(correct);
        radio.selectedProperty().addListener((obs, a, b) -> changed());
        return radio;
    }

    /** 切換單選與多選時保留選項內容；多選改單選時只保留第一個正確答案。 */
    private void switchType(QuestionType before, QuestionType after) {
        if (after != null && after != QuestionType.TRUE_FALSE && before != after) {
            List<String> texts = rows.stream().map(r -> r.text().getText()).toList();
            List<Boolean> marks = rows.stream().map(OptionRow::correct).toList();
            loading = true;
            try {
                rows.clear();
                optionRows.getChildren().clear();
                singleGroup = new ToggleGroup();
                boolean kept = false;
                List<String> defaults = texts.isEmpty() ? List.of("", "", "", "") : texts;
                for (int i = 0; i < defaults.size(); i++) {
                    boolean correct = i < marks.size() && marks.get(i);
                    if (after == QuestionType.SINGLE_CHOICE && correct) {
                        correct = !kept;
                        kept = true;
                    }
                    addRow(defaults.get(i), correct);
                }
            } finally {
                loading = false;
            }
        }
        refreshVisibility();
    }

    private void refreshVisibility() {
        QuestionType t = type.getValue();
        boolean choice = t != QuestionType.TRUE_FALSE;
        optionsBox.setVisible(choice);
        optionsBox.setManaged(choice);
        tfBox.setVisible(!choice);
        tfBox.setManaged(!choice);
    }

    private void relabel() {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).text().setPromptText("選項 " + (char) ('A' + i));
        }
        addOption.setDisable(rows.size() >= Protocol.MAX_OPTIONS);
    }
}
