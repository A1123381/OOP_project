package com.exambattle.client.gui;

import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.bank.ImageData;
import com.exambattle.common.protocol.bank.ImageRef;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 題目圖片的記憶體快取（D-044）：依 id 用 GET_IMAGE 取圖，同一張圖只取一次，最多保留 200 張。
 * 所有方法都在 JavaFX 執行緒上呼叫。
 */
final class ImageCache {

    private static final int MAX_ENTRIES = 200;

    private final GuiSession session;
    private final Map<String, Image> images = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > MAX_ENTRIES;
        }
    };
    private final Map<String, List<Consumer<Image>>> waiting = new HashMap<>();

    ImageCache(GuiSession session) {
        this.session = session;
    }

    /** onLoaded 收到圖片；圖片不存在或讀取失敗時收到 null。 */
    void get(String imageId, Consumer<Image> onLoaded) {
        Image cached = images.get(imageId);
        if (cached != null) {
            onLoaded.accept(cached);
            return;
        }
        List<Consumer<Image>> queue = waiting.get(imageId);
        if (queue != null) {
            queue.add(onLoaded);
            return;
        }
        queue = new ArrayList<>(List.of(onLoaded));
        waiting.put(imageId, queue);
        session.call(MessageType.GET_IMAGE, new ImageRef(imageId), ImageData.class, data -> {
            Image image = fromBytes(Base64.getDecoder().decode(data.data()));
            images.put(imageId, image);
            waiting.remove(imageId).forEach(c -> c.accept(image));
        }, error -> waiting.remove(imageId).forEach(c -> c.accept(null)));
    }

    static Image fromBytes(byte[] bytes) {
        return new Image(new ByteArrayInputStream(bytes));
    }
}
