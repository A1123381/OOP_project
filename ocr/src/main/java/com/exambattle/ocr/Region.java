package com.exambattle.ocr;

import java.awt.image.BufferedImage;

/** 頁面上的一塊矩形區域，座標以頁面寬高的比例表示（0–1，左上角為原點）。 */
public record Region(int page, double x, double y, double width, double height) {

    /** 從這一頁的圖片截出這塊區域（超出邊界的部分會被裁掉）。 */
    public BufferedImage crop(BufferedImage pageImage) {
        int w = pageImage.getWidth();
        int h = pageImage.getHeight();
        int left = clamp((int) Math.floor(x * w), 0, w - 1);
        int top = clamp((int) Math.floor(y * h), 0, h - 1);
        int right = clamp((int) Math.ceil((x + width) * w), left + 1, w);
        int bottom = clamp((int) Math.ceil((y + height) * h), top + 1, h);
        BufferedImage out = new BufferedImage(right - left, bottom - top, BufferedImage.TYPE_INT_RGB);
        out.getGraphics().drawImage(pageImage.getSubimage(left, top, right - left, bottom - top), 0, 0, null);
        return out;
    }

    /** 截圖並去掉四周的空白（接近白色的列與欄），保留少許邊距。整張都是空白時回傳原截圖。 */
    public BufferedImage cropTrimmed(BufferedImage pageImage) {
        return trimWhitespace(crop(pageImage));
    }

    /** 去掉圖片四周接近白色的列與欄。 */
    static BufferedImage trimWhitespace(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int top = 0;
        while (top < h && blankRow(image, top)) {
            top++;
        }
        if (top == h) {
            return image;
        }
        int bottom = h - 1;
        while (bottom > top && blankRow(image, bottom)) {
            bottom--;
        }
        int left = 0;
        while (left < w && blankColumn(image, left, top, bottom)) {
            left++;
        }
        int right = w - 1;
        while (right > left && blankColumn(image, right, top, bottom)) {
            right--;
        }
        int margin = 6;
        left = Math.max(0, left - margin);
        top = Math.max(0, top - margin);
        right = Math.min(w - 1, right + margin);
        bottom = Math.min(h - 1, bottom + margin);
        BufferedImage out = new BufferedImage(right - left + 1, bottom - top + 1, BufferedImage.TYPE_INT_RGB);
        out.getGraphics().drawImage(image.getSubimage(left, top, right - left + 1, bottom - top + 1), 0, 0, null);
        return out;
    }

    private static boolean blankRow(BufferedImage image, int y) {
        for (int x = 0; x < image.getWidth(); x++) {
            if (!light(image.getRGB(x, y))) {
                return false;
            }
        }
        return true;
    }

    private static boolean blankColumn(BufferedImage image, int x, int top, int bottom) {
        for (int y = top; y <= bottom; y++) {
            if (!light(image.getRGB(x, y))) {
                return false;
            }
        }
        return true;
    }

    private static boolean light(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return r > 240 && g > 240 && b > 240;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
