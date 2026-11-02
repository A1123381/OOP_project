-- 第八階段：公開／私人題庫、管理員下架（docs/DECISIONS.md D-039、D-040）。
-- 給第八階段以前建立的資料庫使用；新安裝直接執行 db/schema.sql 即可。可以重複執行（MariaDB 10.6 以上）。
-- 既有考卷維持公開（DEFAULT 'PUBLIC'）；新建考卷的預設值由應用程式決定（私人）。

ALTER TABLE exams
    ADD COLUMN IF NOT EXISTS visibility ENUM ('PUBLIC', 'PRIVATE') NOT NULL DEFAULT 'PUBLIC' AFTER exam_type,
    ADD COLUMN IF NOT EXISTS taken_down TINYINT(1) NOT NULL DEFAULT 0 AFTER visibility;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS is_admin TINYINT(1) NOT NULL DEFAULT 0 AFTER password_iterations;
