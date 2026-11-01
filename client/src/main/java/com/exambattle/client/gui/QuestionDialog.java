package com.exambattle.client.gui;

import com.exambattle.client.gui.model.QuestionDraft;
import com.exambattle.client.gui.model.QuestionGroups;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionType;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 題庫「新增／編輯題目」對話框，內容為 {@link QuestionEditor}，加上所屬題組的選擇（D-034）。
 * 答案可以先不設定（不可推測答案），送出時會提醒。
 */
final class QuestionDialog {

    /** 題組選項：不屬於題組、既有題組、新題組。 */
    private record GroupChoice(Long groupId, String label, boolean create) {
    }

    private static final GroupChoice NONE = new GroupChoice(null, "不屬於題組", false);
    private static final GroupChoice NEW = new GroupChoice(null, "新題組（輸入文章）", true);

    private QuestionDialog() {
    }

    /**
     * @param existing 編輯時為原題目（含答案），新增時為 null
     * @param groups   這份考卷中的題組
     */
    static Optional<QuestionData> show(QuestionData existing, List<QuestionGroups.Group> groups) {
        QuestionEditor editor = new QuestionEditor();
        editor.load(existing == null ? QuestionDraft.empty(QuestionType.SINGLE_CHOICE) : QuestionDraft.from(existing));

        List<GroupChoice> choices = new ArrayList<>();
        choices.add(NONE);
        groups.forEach(g -> choices.add(new GroupChoice(g.id(), g.label() + "：" + abbreviate(g.passage()), false)));
        choices.add(NEW);
        ComboBox<GroupChoice> group = new ComboBox<>();
        group.getItems().setAll(choices);
        group.setConverter(new StringConverter<>() {
            @Override
            public String toString(GroupChoice c) {
                return c == null ? "" : c.label();
            }

            @Override
            public GroupChoice fromString(String s) {
                throw new UnsupportedOperationException();
            }
        });
        group.setValue(choices.stream()
                .filter(c -> existing != null && existing.groupId() != null && existing.groupId().equals(c.groupId()))
                .findFirst().orElse(NONE));
        TextArea passage = new TextArea();
        passage.setWrapText(true);
        passage.setPrefRowCount(6);
        passage.setPromptText("題組文章（閱讀測驗、克漏字等多題共用；克漏字的空格可寫成【11】）");
        passage.visibleProperty().bind(group.valueProperty().isEqualTo(NEW));
        passage.managedProperty().bind(passage.visibleProperty());
        Label hint = new Label("要修改既有題組的文章，請在題庫畫面按「編輯題組文章」（同組題目一起改變）。");
        hint.setStyle("-fx-text-fill: #555;");

        Dialog<QuestionData> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "新增題目" : "編輯題目");
        dialog.setHeaderText("正確答案可先不設定；未設定答案的題目不會用於練習與競賽");
        dialog.setResizable(true);
        VBox content = new VBox(10, new HBox(8, new Label("題組"), group), passage, hint, editor.root());
        content.setPadding(new Insets(10));
        content.setPrefWidth(620);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e -> {
            QuestionDraft current = editor.current();
            Optional<String> error = current.validate();
            if (error.isEmpty() && group.getValue() == NEW) {
                error = passageError(passage.getText());
            }
            if (error.isPresent()) {
                Dialogs.error(error.get());
                e.consume();
            } else if (!current.answerSet() && !Dialogs.confirm("這題尚未設定正確答案，仍要儲存嗎？")) {
                e.consume();
            }
        });
        dialog.setResultConverter(b -> {
            if (b != ButtonType.OK) {
                return null;
            }
            QuestionData data = editor.current().toData();
            GroupChoice choice = group.getValue();
            return choice.create() ? data.withGroup(null, passage.getText().strip()) : data.withGroup(choice.groupId(), null);
        });
        return dialog.showAndWait();
    }

    static Optional<String> passageError(String text) {
        String s = text == null ? "" : text.strip();
        if (s.isEmpty()) {
            return Optional.of("請輸入題組文章");
        }
        if (s.length() > Protocol.MAX_PASSAGE_CHARS) {
            return Optional.of("題組文章不可超過 " + Protocol.MAX_PASSAGE_CHARS + " 字元");
        }
        return Optional.empty();
    }

    private static String abbreviate(String s) {
        String line = s == null ? "" : s.strip().replaceAll("\\s+", " ");
        return line.length() <= 30 ? line : line.substring(0, 30) + "…";
    }
}
