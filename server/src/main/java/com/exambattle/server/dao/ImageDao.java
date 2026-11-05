package com.exambattle.server.dao;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/** images 資料表的存取介面（題目圖片，D-043）。 */
public interface ImageDao {

    /** images 的一列。 */
    record ImageRecord(String id, String sha256, String mime, int width, int height, byte[] bytes, long createdBy,
                       Instant createdAt) {

        /** 避免圖片內容出現在日誌中。 */
        @Override
        public String toString() {
            return "ImageRecord[id=" + id + ", mime=" + mime + ", " + width + "x" + height + ", " + bytes.length + " bytes]";
        }
    }

    /** 新增圖片；相同 sha256 已存在時不新增，回傳既有的那一張。 */
    ImageRecord saveOrGet(ImageRecord image);

    Optional<ImageRecord> find(String id);

    /** 這些 id 中存在的圖片 id。 */
    Set<String> existing(Collection<String> ids);

    /** 刪除沒有被任何題目、選項、題組引用，且建立時間早於 cutoff 的圖片；回傳刪除的數量。 */
    int deleteUnreferencedBefore(Instant cutoff);
}
