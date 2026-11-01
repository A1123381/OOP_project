-- 題組（閱讀測驗、克漏字、文意選填、篇章結構：多題共用一篇文章），見 docs/DECISIONS.md D-034。
-- 給第七階段（含）以前建立的資料庫使用；新安裝直接執行 db/schema.sql 即可，不需要這個檔案。
-- 只能執行一次（MariaDB 10.6 以上可重複執行：IF NOT EXISTS）。

CREATE TABLE IF NOT EXISTS question_groups (
    id         BIGINT     NOT NULL AUTO_INCREMENT,
    passage    MEDIUMTEXT NOT NULL,
    created_by BIGINT     NOT NULL,
    created_at TIMESTAMP  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_groups_creator FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

ALTER TABLE questions
    ADD COLUMN IF NOT EXISTS group_id BIGINT NULL AFTER image_path,
    ADD KEY IF NOT EXISTS idx_questions_group (group_id),
    ADD CONSTRAINT fk_questions_group FOREIGN KEY IF NOT EXISTS (group_id) REFERENCES question_groups (id) ON DELETE SET NULL;
