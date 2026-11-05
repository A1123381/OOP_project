package com.exambattle.client.gui;

import com.exambattle.client.gui.model.DraftImage;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;

/** 題目圖片的顯示（D-043）：載入中與不存在時顯示文字，點一下放大檢視。 */
final class ImageViews {

    private ImageViews() {
    }

    /** 選項圖片的最大顯示寬度。 */
    static final double OPTION_WIDTH = 480;
    /** 題幹與題組文章圖片的最大顯示寬度。 */
    static final double STEM_WIDTH = 640;

    /** 在單選鈕、核取方塊或文字標籤下方加上圖片；imageId 為 null 時不變。 */
    static void decorate(javafx.scene.control.Labeled labeled, ImageCache cache, String imageId, double maxWidth) {
        Node view = of(cache, imageId, maxWidth);
        if (view != null) {
            labeled.setGraphic(view);
            labeled.setContentDisplay(javafx.scene.control.ContentDisplay.BOTTOM);
            labeled.setGraphicTextGap(6);
        }
    }

    /** 內容下方接一張圖片；imageId 為 null 時直接回傳內容。 */
    static Node withImage(Node content, ImageCache cache, String imageId, double maxWidth) {
        Node view = of(cache, imageId, maxWidth);
        return view == null ? content : new javafx.scene.layout.VBox(8, content, view);
    }

    /** 選項的顯示文字：只有圖片時標示「（圖片）」。 */
    static String optionText(String letter, String content, String imageId) {
        boolean blank = content == null || content.isBlank();
        return letter + ". " + (blank && imageId != null ? "（圖片）" : content == null ? "" : content);
    }

    /** imageId 為 null 時回傳 null（沒有圖片）。 */
    static Node of(ImageCache cache, String imageId, double maxWidth) {
        return imageId == null ? null : of(cache, DraftImage.uploaded(imageId), maxWidth);
    }

    static Node of(ImageCache cache, DraftImage image, double maxWidth) {
        if (image == null) {
            return null;
        }
        StackPane box = new StackPane();
        box.setMaxWidth(maxWidth);
        box.setStyle("-fx-alignment: center-left;");
        if (image.bytes() != null) {
            show(box, ImageCache.fromBytes(image.bytes()), maxWidth);
        } else {
            Label loading = new Label("圖片載入中…");
            loading.setStyle("-fx-text-fill: #667;");
            box.getChildren().setAll(loading);
            cache.get(image.imageId(), loaded -> {
                if (loaded == null) {
                    Label missing = new Label("（圖片已不存在）");
                    missing.setStyle("-fx-text-fill: #888;");
                    box.getChildren().setAll(missing);
                } else {
                    show(box, loaded, maxWidth);
                }
            });
        }
        return box;
    }

    private static void show(StackPane box, Image image, double maxWidth) {
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        view.setFitWidth(Math.min(maxWidth, image.getWidth()));
        view.setSmooth(true);
        view.setStyle("-fx-cursor: hand;");
        view.setOnMouseClicked(e -> enlarge(image));
        box.getChildren().setAll(view);
    }

    /** 放大檢視：最大為螢幕的 90%。 */
    static void enlarge(Image image) {
        ImageView view = new ImageView(image);
        view.setPreserveRatio(true);
        double maxW = Screen.getPrimary().getVisualBounds().getWidth() * 0.9;
        double maxH = Screen.getPrimary().getVisualBounds().getHeight() * 0.9;
        view.setFitWidth(Math.min(maxW, image.getWidth()));
        ScrollPane scroll = new ScrollPane(view);
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("圖片");
        stage.setScene(new Scene(scroll, Math.min(maxW, image.getWidth() + 20), Math.min(maxH, image.getHeight() + 20)));
        view.setOnMouseClicked(e -> stage.close());
        stage.show();
    }

    /** JavaFX Image 轉成 BufferedImage（剪貼簿圖片用，不依賴 javafx-swing）。 */
    static BufferedImage toBuffered(Image image) {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                out.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        return out;
    }

    /** BufferedImage 轉成 JavaFX Image。 */
    static Image toFx(BufferedImage image) {
        WritableImage fx = new WritableImage(image.getWidth(), image.getHeight());
        var writer = fx.getPixelWriter();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                writer.setArgb(x, y, image.getRGB(x, y));
            }
        }
        return fx;
    }
}
