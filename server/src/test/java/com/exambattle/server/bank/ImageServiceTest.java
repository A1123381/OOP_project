package com.exambattle.server.bank;

import com.exambattle.common.protocol.bank.ImageUpload;
import com.exambattle.server.dao.InMemoryImageDao;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ImageServiceTest {

    private static String png(int width) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, 2, BufferedImage.TYPE_INT_RGB), "png", out);
        return Base64.getEncoder().encodeToString(out.toByteArray());
    }

    @Test
    void cleanupRemovesOnlyOldUnreferencedImages() throws Exception {
        Set<String> referenced = new HashSet<>();
        InMemoryImageDao dao = new InMemoryImageDao(() -> referenced);
        Instant t0 = Instant.parse("2026-11-05T00:00:00Z");
        ImageService early = new ImageService(dao, Clock.fixed(t0, ZoneOffset.UTC));
        String used = early.upload(1, new ImageUpload("image/png", png(1))).imageId();
        String unused = early.upload(1, new ImageUpload("image/png", png(2))).imageId();
        referenced.add(used);

        ImageService soon = new ImageService(dao, Clock.fixed(t0.plus(Duration.ofHours(23)), ZoneOffset.UTC));
        assertEquals(0, soon.cleanup(), "24 小時內保留（可能尚未匯入）");

        ImageService later = new ImageService(dao, Clock.fixed(t0.plus(Duration.ofHours(25)), ZoneOffset.UTC));
        assertEquals(1, later.cleanup());
        later.get(used);
        assertThrows(BankException.class, () -> later.get(unused));
    }
}
