package com.exambattle.client.gui;

import com.exambattle.client.gui.model.ExamDraft;
import com.exambattle.common.protocol.bank.ExamRequest;
import com.exambattle.common.protocol.bank.ExamSummary;
import com.exambattle.common.protocol.bank.SubjectInfo;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.List;
import java.util.Optional;

/** 新增或編輯考卷的對話框。 */
final class ExamDialog {

    private ExamDialog() {
    }

    /**
     * @param existing 編輯時為原考卷，新增時為 null
     * @param preset   新增時預先選取的分類，可為 null
     */
    static Optional<ExamRequest> show(List<SubjectInfo> subjects, ExamSummary existing, SubjectInfo preset) {
        Dialog<ExamRequest> dialog = new Dialog<>();
        dialog.setTitle(existing == null ? "新增考卷" : "編輯考卷");
        dialog.setHeaderText("年份與考試類型可留空");

        TextField title = new TextField(existing == null ? "" : existing.title());
        title.setPrefColumnCount(28);
        ComboBox<SubjectInfo> subject = new ComboBox<>();
        subject.getItems().setAll(subjects);
        subject.setConverter(SubjectCells.CONVERTER);
        long selectedId = existing != null ? existing.subject().id() : preset != null ? preset.id() : -1;
        subjects.stream().filter(s -> s.id() == selectedId).findFirst().ifPresent(subject::setValue);
        TextField year = new TextField(existing == null || existing.year() == null ? "" : existing.year().toString());
        year.setPromptText("例如 2024");
        TextField type = new TextField(existing == null || existing.examType() == null ? "" : existing.examType());
        type.setPromptText("例如 期中考、學測、證照");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("考卷名稱"), title);
        form.addRow(1, new Label("分類"), subject);
        form.addRow(2, new Label("考試年份"), year);
        form.addRow(3, new Label("考試類型"), type);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e -> {
            Optional<String> error = draft(title, subject, year, type).validate();
            if (error.isPresent()) {
                Dialogs.error(error.get());
                e.consume();
            }
        });
        dialog.setResultConverter(b -> b == ButtonType.OK
                ? draft(title, subject, year, type).toRequest(existing == null ? null : existing.id())
                : null);
        return dialog.showAndWait();
    }

    private static ExamDraft draft(TextField title, ComboBox<SubjectInfo> subject, TextField year, TextField type) {
        return new ExamDraft(title.getText(), subject.getValue(), year.getText(), type.getText());
    }
}
