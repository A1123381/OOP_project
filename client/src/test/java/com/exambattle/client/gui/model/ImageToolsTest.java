package com.exambattle.client.gui.model;

import com.exambattle.common.protocol.Protocol;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageToolsTest {

    @Test
    void smallDiagramStaysPngAtOriginalSize() throws Exception {
        BufferedImage source = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = source.createGraphics();
        g.setColor(Color.BLACK);
        g.drawRect(10, 10, 200, 100);
        g.dispose();

        DraftImage image = ImageTools.prepare(source);

        assertNull(image.imageId(), "準備好的圖片還沒上傳");
        assertTrue(image.pending());
        assertEquals("image/png", image.mime());
        BufferedImage back = ImageTools.read(image.bytes());
        assertEquals(400, back.getWidth());
        assertEquals(300, back.getHeight());
    }

    @Test
    void largeNoisyPhotoIsScaledAndCompressedUnderOneMegabyte() throws Exception {
        // 雜訊圖 PNG 壓縮效果極差，迫使改用 JPEG
        BufferedImage source = new BufferedImage(3000, 2000, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(42);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                source.setRGB(x, y, random.nextInt(0xFFFFFF));
            }
        }

        DraftImage image = ImageTools.prepare(source);

        assertTrue(image.bytes().length <= Protocol.MAX_IMAGE_BYTES, "每張最多 1 MB：" + image.bytes().length);
        assertEquals("image/jpeg", image.mime());
        BufferedImage back = ImageTools.read(image.bytes());
        assertTrue(back.getWidth() <= Protocol.IMAGE_TARGET_WIDTH, "寬度最多 1600：" + back.getWidth());
        assertEquals(back.getWidth() * 2 / 3.0, back.getHeight(), 2, "保持長寬比");
    }

    @Test
    void sameContentGivesEqualImages() {
        DraftImage a = DraftImage.local(new byte[] {1, 2, 3}, "image/png");
        DraftImage b = DraftImage.local(new byte[] {1, 2, 3}, "image/png");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals("x", a.withId("x").imageId());
        assertEquals(3, a.withId("x").bytes().length, "上傳後保留本機內容");
    }
}
