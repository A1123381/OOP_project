package com.exambattle.common.protocol.bank;

/**
 * UPLOAD_IMAGE：上傳一張題目圖片（D-044）。data 是 Base64 的檔案內容，mime 為 image/png 或 image/jpeg。
 * 上傳後要在 24 小時內被題目、選項或題組引用，否則會被清除。
 */
public record ImageUpload(String mime, String data) {
}
