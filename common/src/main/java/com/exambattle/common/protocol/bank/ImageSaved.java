package com.exambattle.common.protocol.bank;

/** IMAGE_SAVED：圖片的 id 與尺寸；內容相同的圖片回傳同一個 id。 */
public record ImageSaved(String imageId, int width, int height) {
}
