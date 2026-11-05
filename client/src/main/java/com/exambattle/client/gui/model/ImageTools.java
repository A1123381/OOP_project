package com.exambattle.client.gui.model;

import com.exambattle.common.protocol.Protocol;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 準備要上傳的題目圖片（D-044），不依賴 JavaFX：
 * 寬度縮小到最多 1600 像素；先存成 PNG，超過 1 MB 改成 JPEG 並逐步降低品質，必要時再縮小，直到 1 MB 以內。
 */
public final class ImageTools {

    private ImageTools() {
    }

    /** 讀取 PNG／JPG／GIF／BMP 檔並準備成可上傳的圖片。 */
    public static DraftImage fromFile(Path file) throws IOException {
        BufferedImage image = ImageIO.read(Files.newInputStream(file));
        if (image == null) {
            throw new IOException("無法讀取圖片：" + file.getFileName());
        }
        return prepare(image);
    }

    public static DraftImage prepare(BufferedImage source) {
        BufferedImage image = toRgb(scaleToWidth(source, Protocol.IMAGE_TARGET_WIDTH));
        while (true) {
            byte[] png = encode(image, "png");
            if (png.length <= Protocol.MAX_IMAGE_BYTES) {
                return DraftImage.local(png, "image/png");
            }
            for (float quality = 0.9f; quality >= 0.4f; quality -= 0.1f) {
                byte[] jpeg = jpeg(image, quality);
                if (jpeg.length <= Protocol.MAX_IMAGE_BYTES) {
                    return DraftImage.local(jpeg, "image/jpeg");
                }
            }
            image = scaleToWidth(image, (int) (image.getWidth() * 0.75));
        }
    }

    /** 讀回圖片內容（顯示用）。 */
    public static BufferedImage read(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IOException("無法讀取圖片");
        }
        return image;
    }

    static BufferedImage scaleToWidth(BufferedImage image, int maxWidth) {
        if (image.getWidth() <= maxWidth) {
            return image;
        }
        int height = Math.max(1, (int) Math.round(image.getHeight() * (double) maxWidth / image.getWidth()));
        BufferedImage scaled = new BufferedImage(maxWidth, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, maxWidth, height);
        g.drawImage(image, 0, 0, maxWidth, height, null);
        g.dispose();
        return scaled;
    }

    /** 透明背景改成白色（JPEG 不支援透明）。 */
    private static BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return rgb;
    }

    private static byte[] encode(BufferedImage image, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] jpeg(BufferedImage image, float quality) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(stream);
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
                writer.write(null, new IIOImage(image, null, null), param);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            writer.dispose();
        }
    }
}
