package com.exambattle.client.gui;

import com.exambattle.client.gui.model.QuestionDraft;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionType;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.layout.VBox;

import java.util.Optional;

/**
 * 題庫「新增／編輯題目」對話框，內容為 {@link QuestionEditor}。
 * 答案可以先不設定（不可推測答案），送出時會提醒。
 */
final class QuestionDialog {

    private QuestionDialog() {
    }

    /** @param existing 編輯時為原題目（含答案），新增時為 null */
    static Optional<QuestionData> show(QuestionData existing) {
        QuestionEditor editor = new QuestionEditor();
        editor.load(existing == null ? QuestionDraft.empty(QuestionType.SINGLE_CHOICE) : QuestionDraft.from(existing));

        Dialog<QuestionData> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "新增題目" : "編輯題目");
        dialog.setHeaderText("正確答案可先不設定；未設定答案的題目不會用於練習與競賽");
        dialog.setResizable(true);
        VBox content = new VBox(editor.root());
        content.setPadding(new Insets(10));
        content.setPrefWidth(620);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e -> {
            QuestionDraft current = editor.current();
            Optional<String> error = current.validate();
            if (error.isPresent()) {
                Dialogs.error(error.get());
                e.consume();
            } else if (!current.answerSet() && !Dialogs.confirm("這題尚未設定正確答案，仍要儲存嗎？")) {
                e.consume();
            }
        });
        dialog.setResultConverter(b -> b == ButtonType.OK ? editor.current().toData() : null);
        return dialog.showAndWait();
    }
}
