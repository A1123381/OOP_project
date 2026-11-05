-- 題目圖片（題幹、選項、題組文章），見 docs/DECISIONS.md D-043。
-- 給 1.0.0 以前建立的資料庫使用；新安裝直接執行 db/schema.sql 即可。可以重複執行（MariaDB 10.6 以上）。

CREATE TABLE IF NOT EXISTS images (
    id         CHAR(32)     NOT NULL,
    sha256     CHAR(64)     NOT NULL,
    mime       VARCHAR(20)  NOT NULL,
    width      INT          NOT NULL,
    height     INT          NOT NULL,
    bytes      MEDIUMBLOB   NOT NULL,
    created_by BIGINT       NOT NULL,
    created_at DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_images_sha256 (sha256),
    KEY idx_images_created (created_at),
    CONSTRAINT fk_images_creator FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

ALTER TABLE questions
    ADD COLUMN IF NOT EXISTS image_id CHAR(32) NULL AFTER image_path,
    ADD CONSTRAINT fk_questions_image FOREIGN KEY IF NOT EXISTS (image_id) REFERENCES images (id) ON DELETE SET NULL;

ALTER TABLE question_options
    ADD COLUMN IF NOT EXISTS image_id CHAR(32) NULL AFTER content,
    ADD CONSTRAINT fk_options_image FOREIGN KEY IF NOT EXISTS (image_id) REFERENCES images (id) ON DELETE SET NULL;

ALTER TABLE question_groups
    ADD COLUMN IF NOT EXISTS image_id CHAR(32) NULL AFTER passage,
    ADD CONSTRAINT fk_groups_image FOREIGN KEY IF NOT EXISTS (image_id) REFERENCES images (id) ON DELETE SET NULL;
