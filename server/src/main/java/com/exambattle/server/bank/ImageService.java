package com.exambattle.server.bank;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.bank.ImageData;
import com.exambattle.common.protocol.bank.ImageSaved;
import com.exambattle.common.protocol.bank.ImageUpload;
import com.exambattle.server.dao.ImageDao;
import com.exambattle.server.dao.ImageDao.ImageRecord;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 題目圖片（D-043、D-044）：
 * <ul>
 *   <li>只接受 PNG、JPEG：檢查檔頭與宣告的類型一致，且讀得出寬高；最多 1 MB、寬高各 4000 像素</li>
 *   <li>id 是隨機 32 個十六進位字元，知道 id 就能取圖（圖片不是答案，只會出現在看得到的題目中）</li>
 *   <li>內容相同（sha256）的圖片只存一份</li>
 *   <li>沒有被引用且超過 24 小時的圖片定時清除（保留已上傳、尚未匯入的圖片）</li>
 * </ul>
 */
public final class ImageService implements AutoCloseable {

    /** 上傳後多久沒被引用就清除。 */
    public static final Duration UNREFERENCED_TTL = Duration.ofHours(24);
    private static final Duration CLEANUP_INTERVAL = Duration.ofHours(1);
    private static final Logger LOG = Logger.getLogger(ImageService.class.getName());
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final ImageDao images;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private ScheduledExecutorService cleaner;

    public ImageService(ImageDao images, Clock clock) {
        this.images = images;
        this.clock = clock;
    }

    public ImageSaved upload(long userId, ImageUpload upload) throws BankException {
        if (upload == null || upload.mime() == null || upload.data() == null) {
            throw invalid("缺少 mime 或 data");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(upload.data());
        } catch (IllegalArgumentException e) {
            throw invalid("圖片資料不是有效的 Base64");
        }
        if (bytes.length == 0 || bytes.length > Protocol.MAX_IMAGE_BYTES) {
            throw invalid("圖片必須在 " + Protocol.MAX_IMAGE_BYTES / 1024 + " KB 以內");
        }
        String mime = upload.mime();
        boolean png = startsWith(bytes, PNG);
        boolean jpeg = startsWith(bytes, JPEG);
        if (!(mime.equals("image/png") && png) && !(mime.equals("image/jpeg") && jpeg)) {
            throw invalid("只接受 PNG 或 JPEG 圖片");
        }
        int[] size = dimensions(bytes);
        if (size[0] > Protocol.MAX_IMAGE_SIDE || size[1] > Protocol.MAX_IMAGE_SIDE) {
            throw invalid("圖片寬高各不可超過 " + Protocol.MAX_IMAGE_SIDE + " 像素");
        }
        byte[] id = new byte[16];
        random.nextBytes(id);
        ImageRecord saved = images.saveOrGet(new ImageRecord(HexFormat.of().formatHex(id), sha256(bytes), mime,
                size[0], size[1], bytes, userId, clock.instant()));
        return new ImageSaved(saved.id(), saved.width(), saved.height());
    }

    public ImageData get(String imageId) throws BankException {
        ImageRecord image = imageId == null ? null : images.find(imageId).orElse(null);
        if (image == null) {
            throw new BankException(ErrorCode.IMAGE_NOT_FOUND, "找不到圖片");
        }
        return new ImageData(image.id(), image.mime(), Base64.getEncoder().encodeToString(image.bytes()));
    }

    /** 清除一次未引用的舊圖片；回傳刪除的數量。 */
    public int cleanup() {
        return images.deleteUnreferencedBefore(clock.instant().minus(UNREFERENCED_TTL));
    }

    /** 伺服器啟動時開始定時清除（立即一次，之後每小時）。 */
    public synchronized void startCleanup() {
        if (cleaner != null) {
            return;
        }
        cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "image-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleWithFixedDelay(() -> {
            try {
                int removed = cleanup();
                if (removed > 0) {
                    LOG.info(() -> "已清除 " + removed + " 張未使用的圖片");
                }
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "清除未使用的圖片失敗", e);
            }
        }, 0, CLEANUP_INTERVAL.toMinutes(), TimeUnit.MINUTES);
    }

    @Override
    public synchronized void close() {
        if (cleaner != null) {
            cleaner.shutdownNow();
            cleaner = null;
        }
    }

    private static int[] dimensions(byte[] bytes) throws BankException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                throw invalid("無法讀取圖片");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new int[] {reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw invalid("無法讀取圖片");
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (bytes[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BankException invalid(String message) {
        return new BankException(ErrorCode.IMAGE_INVALID, message);
    }
}
