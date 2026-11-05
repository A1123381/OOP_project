package com.exambattle.client.gui.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * 題目、選項或題組文章的一張圖片（D-043）。
 * <ul>
 *   <li>已在伺服器上：imageId 不為 null，bytes 可以是 null（需要時再用 GET_IMAGE 取）</li>
 *   <li>還在本機（剛截圖、貼上或選檔案）：imageId 為 null，bytes 是 PNG／JPEG 檔案內容，儲存或匯入前才上傳</li>
 * </ul>
 */
public record DraftImage(String imageId, byte[] bytes, String mime) {

    public static DraftImage uploaded(String imageId) {
        return new DraftImage(imageId, null, null);
    }

    public static DraftImage local(byte[] bytes, String mime) {
        return new DraftImage(null, bytes.clone(), mime);
    }

    /** 還沒上傳到伺服器。 */
    public boolean pending() {
        return imageId == null;
    }

    /** 上傳完成後換成伺服器的 id（保留本機內容，顯示時不用再下載）。 */
    public DraftImage withId(String id) {
        return new DraftImage(id, bytes, mime);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof DraftImage d && Objects.equals(imageId, d.imageId) && Arrays.equals(bytes, d.bytes)
                && Objects.equals(mime, d.mime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(imageId, Arrays.hashCode(bytes), mime);
    }

    @Override
    public String toString() {
        return "DraftImage[" + (imageId != null ? imageId : "local") + (bytes == null ? "" : ", " + bytes.length + " bytes")
                + "]";
    }
}
