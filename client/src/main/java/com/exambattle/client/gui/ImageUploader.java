package com.exambattle.client.gui;

import com.exambattle.client.gui.model.DraftImage;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.bank.ImageSaved;
import com.exambattle.common.protocol.bank.ImageUpload;

import java.util.Base64;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 逐張上傳本機的題目圖片（D-044），每張一則 UPLOAD_IMAGE；全部完成後回傳「本機圖片 → 伺服器 id」。
 * 回呼都在 JavaFX 執行緒上執行。
 */
final class ImageUploader {

    private ImageUploader() {
    }

    /**
     * @param progress 每上傳完一張呼叫一次（已完成張數、總張數）；可為 null
     */
    static void upload(GuiSession session, List<DraftImage> pending, Progress progress,
                       Consumer<Map<DraftImage, String>> done, Consumer<String> onError) {
        Map<DraftImage, String> ids = new IdentityHashMap<>();
        next(session, pending, 0, ids, progress, done, onError);
    }

    /** 上傳進度。 */
    interface Progress {
        void update(int uploaded, int total);
    }

    private static void next(GuiSession session, List<DraftImage> pending, int index, Map<DraftImage, String> ids,
                             Progress progress, Consumer<Map<DraftImage, String>> done, Consumer<String> onError) {
        if (index >= pending.size()) {
            done.accept(ids);
            return;
        }
        DraftImage image = pending.get(index);
        session.call(MessageType.UPLOAD_IMAGE, new ImageUpload(image.mime(), Base64.getEncoder().encodeToString(image.bytes())),
                ImageSaved.class, saved -> {
                    ids.put(image, saved.imageId());
                    if (progress != null) {
                        progress.update(index + 1, pending.size());
                    }
                    next(session, pending, index + 1, ids, progress, done, onError);
                }, error -> onError.accept("圖片上傳失敗：" + error));
    }
}
