package com.exambattle.client.gui;

import com.exambattle.client.gui.model.ExamDraft;
import com.exambattle.common.protocol.bank.ExamRequest;
import com.exambattle.common.protocol.bank.ExamSummary;
import com.exambattle.common.protocol.bank.SubjectInfo;
import com.exambattle.common.protocol.bank.Visibility;
import javafx.event.ActionEvent;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
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

        // 公開範圍（第八階段）：新增時預設私人；被管理員下架時不能公開
        ToggleGroup scope = new ToggleGroup();
        RadioButton privateScope = new RadioButton("私人（只有我看得到）");
        RadioButton publicScope = new RadioButton("公開（所有人都能瀏覽、練習、競賽）");
        privateScope.setToggleGroup(scope);
        publicScope.setToggleGroup(scope);
        boolean takenDown = existing != null && existing.takenDown();
        (existing != null && existing.visibility() == Visibility.PUBLIC ? publicScope : privateScope).setSelected(true);
        publicScope.setDisable(takenDown);
        VBox scopeBox = new VBox(6, new HBox(16, privateScope, publicScope));
        if (takenDown) {
            Label notice = new Label("這份考卷已被管理員下架，暫時不能公開；請洽管理員。");
            notice.setStyle("-fx-text-fill: #c62828;");
            scopeBox.getChildren().add(notice);
        }

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("考卷名稱"), title);
        form.addRow(1, new Label("分類"), subject);
        form.addRow(2, new Label("考試年份"), year);
        form.addRow(3, new Label("考試類型"), type);
        form.addRow(4, new Label("公開範圍"), scopeBox);
        dialog.getDialogPane().setContent(form);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.getDialogPane().lookupButton(ButtonType.OK).addEventFilter(ActionEvent.ACTION, e -> {
            Optional<String> error = draft(title, subject, year, type, publicScope).validate();
            if (error.isPresent()) {
                Dialogs.error(error.get());
                e.consume();
            }
        });
        dialog.setResultConverter(b -> b == ButtonType.OK
                ? draft(title, subject, year, type, publicScope).toRequest(existing == null ? null : existing.id())
                : null);
        return dialog.showAndWait();
    }

    private static ExamDraft draft(TextField title, ComboBox<SubjectInfo> subject, TextField year, TextField type,
                                   RadioButton publicScope) {
        return new ExamDraft(title.getText(), subject.getValue(), year.getText(), type.getText(),
                publicScope.isSelected() ? Visibility.PUBLIC : Visibility.PRIVATE);
    }
}
