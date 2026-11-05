package com.exambattle.client.gui;

import com.exambattle.client.gui.model.DraftImage;
import com.exambattle.client.gui.model.ImageTools;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * 一個可以放圖片的位置（題幹、選項或題組文章，D-043）：縮圖加「圖片」選單，
 * 可以選擇檔案、從剪貼簿貼上（例如 Win+Shift+S 截圖後）、從原始頁面框選（匯入校正時）或移除。
 */
final class ImageSlot {

    /** 框選：主畫面進入框選模式，完成後把截下的圖片交給 receiver。 */
    interface FrameHandler {
        void startFraming(String target, Consumer<DraftImage> receiver);
    }

    private final ImageCache cache;
    private final String target;
    private final double thumbWidth;
    private final HBox root = new HBox(8);
    private final HBox thumb = new HBox();
    private final MenuButton menu = new MenuButton("圖片");
    private final MenuItem frame = new MenuItem("從原始頁面框選…");
    private final MenuItem remove = new MenuItem("移除圖片");
    private DraftImage value;
    private Runnable onChange = () -> { };
    private FrameHandler frameHandler;

    /** @param target 這個位置的名稱，例如「題目」「選項 A」（框選時顯示） */
    ImageSlot(ImageCache cache, String target, double thumbWidth) {
        this.cache = cache;
        this.target = target;
        this.thumbWidth = thumbWidth;
        MenuItem choose = new MenuItem("選擇圖片檔…");
        choose.setOnAction(e -> chooseFile());
        MenuItem paste = new MenuItem("從剪貼簿貼上");
        paste.setOnAction(e -> paste());
        frame.setOnAction(e -> {
            if (frameHandler != null) {
                frameHandler.startFraming(target, this::set);
            }
        });
        remove.setOnAction(e -> set(null));
        menu.getItems().setAll(choose, paste, frame, remove);
        root.setAlignment(Pos.CENTER_LEFT);
        root.getChildren().setAll(thumb, menu);
        refresh();
    }

    Parent root() {
        return root;
    }

    DraftImage value() {
        return value;
    }

    /** 載入時呼叫，不觸發 onChange。 */
    void load(DraftImage image) {
        value = image;
        refresh();
    }

    void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    void setFrameHandler(FrameHandler handler) {
        this.frameHandler = handler;
        refresh();
    }

    void set(DraftImage image) {
        value = image;
        refresh();
        onChange.run();
    }

    private void refresh() {
        Node view = ImageViews.of(cache, value, thumbWidth);
        thumb.getChildren().setAll(view == null ? new Label() : view);
        thumb.setVisible(view != null);
        thumb.setManaged(view != null);
        frame.setVisible(frameHandler != null);
        remove.setDisable(value == null);
        menu.setText(value == null ? "加圖片" : "換圖片");
    }

    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("選擇" + target + "的圖片");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("圖片（PNG、JPG、GIF、BMP）",
                "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File file = chooser.showOpenDialog(root.getScene() == null ? null : root.getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            set(ImageTools.fromFile(file.toPath()));
        } catch (IOException e) {
            Dialogs.error(e.getMessage());
        }
    }

    private void paste() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        try {
            if (clipboard.hasImage()) {
                set(ImageTools.prepare(ImageViews.toBuffered(clipboard.getImage())));
            } else if (clipboard.hasFiles() && !clipboard.getFiles().isEmpty()) {
                set(ImageTools.fromFile(clipboard.getFiles().get(0).toPath()));
            } else {
                Dialogs.error("剪貼簿裡沒有圖片。可以先用截圖工具（例如 Win+Shift+S）截圖，再按「從剪貼簿貼上」。");
            }
        } catch (IOException e) {
            Dialogs.error(e.getMessage());
        }
    }
}
