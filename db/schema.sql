-- Exam Battle 資料表結構（MariaDB 10.6 以上，也相容 MySQL 8）
-- 擁有者：db-dev。只建立資料表，不建立資料庫與帳號；設定步驟見 docs/DATABASE.md。
-- 可重複執行（CREATE TABLE IF NOT EXISTS）。
--
-- 第二階段：users。第三階段：subjects、exams、questions、question_options、exam_questions。
-- 第五階段：practice_records、answer_records、wrong_questions（game_records 保留給第四階段的多人競賽）。
-- 題組支援：question_groups、questions.group_id（既有資料庫請執行 db/migrations/001_question_groups.sql）。
-- 科目與題目內容一律由使用者建立；初始大分類放在 db/seed_subjects.sql（資料，不是程式）。

CREATE TABLE IF NOT EXISTS users (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    -- utf8mb4_unicode_ci 不分大小寫，因此 alice 與 ALICE 視為同一帳號
    username            VARCHAR(20)  NOT NULL COLLATE utf8mb4_unicode_ci,
    display_name        VARCHAR(32)  NOT NULL,
    -- PBKDF2WithHmacSHA256 雜湊結果，不保存明文密碼
    password_hash       VARBINARY(32) NOT NULL,
    password_salt       VARBINARY(16) NOT NULL,
    password_iterations INT          NOT NULL,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 大分類（科目）。parent_id 保留給之後的子分類，第三階段一律為 NULL。
CREATE TABLE IF NOT EXISTS subjects (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50) NOT NULL COLLATE utf8mb4_unicode_ci,
    parent_id   BIGINT      NULL,
    -- 種子資料為 NULL
    created_by  BIGINT      NULL,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subjects_name (name),
    CONSTRAINT fk_subjects_parent FOREIGN KEY (parent_id) REFERENCES subjects (id),
    CONSTRAINT fk_subjects_creator FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 考卷基本資料。exam_type 為自由文字（期中考、學測、證照…），不限定內容。
CREATE TABLE IF NOT EXISTS exams (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    title       VARCHAR(200) NOT NULL,
    subject_id  BIGINT       NOT NULL,
    exam_year   SMALLINT     NULL,
    exam_type   VARCHAR(50)  NULL,
    created_by  BIGINT       NOT NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_exams_subject (subject_id),
    CONSTRAINT fk_exams_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
    CONSTRAINT fk_exams_creator FOREIGN KEY (created_by) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 題組：閱讀測驗、克漏字等多題共用一篇文章（D-034）。文章只存一份；
-- 題組不再有任何題目時由應用程式刪除。
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

-- 題目與考卷分開管理（見 exam_questions），同一題日後可放進多份考卷。
-- true_false_answer：是非題答案，NULL 表示尚未設定（不得自行推測答案）。
-- image_path：保留給之後的題目圖片，第三階段不使用。
-- group_id：所屬題組；NULL 表示獨立的題目。
CREATE TABLE IF NOT EXISTS questions (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    type              ENUM ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE') NOT NULL,
    content           TEXT         NOT NULL,
    explanation       TEXT         NULL,
    true_false_answer TINYINT(1)   NULL,
    image_path        VARCHAR(255) NULL,
    group_id          BIGINT       NULL,
    created_by        BIGINT       NOT NULL,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_questions_group (group_id),
    CONSTRAINT fk_questions_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_questions_group FOREIGN KEY (group_id) REFERENCES question_groups (id) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 選擇題的選項；沒有任何 is_correct = 1 表示答案尚未設定。
CREATE TABLE IF NOT EXISTS question_options (
    id          BIGINT     NOT NULL AUTO_INCREMENT,
    question_id BIGINT     NOT NULL,
    position    INT        NOT NULL,
    content     TEXT       NOT NULL,
    is_correct  TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_options_question (question_id, position),
    CONSTRAINT fk_options_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 考卷包含哪些題目與順序。position 不設唯一鍵，重新排序時不會暫時衝突；
-- 讀取時依 (position, question_id) 排序。
CREATE TABLE IF NOT EXISTS exam_questions (
    exam_id     BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    position    INT    NOT NULL,
    PRIMARY KEY (exam_id, question_id),
    KEY idx_exam_questions_question (question_id),
    CONSTRAINT fk_eq_exam FOREIGN KEY (exam_id) REFERENCES exams (id) ON DELETE CASCADE,
    CONSTRAINT fk_eq_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 一次單人練習或考試的紀錄（需求 3.7 的考試紀錄、分數、作答時間）。
-- 時間一律以 UTC 寫入 DATETIME(3)；source_* 只作參考，不設 FK（考卷或分類之後可能被刪除）。
CREATE TABLE IF NOT EXISTS practice_records (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    user_id           BIGINT       NOT NULL,
    mode              VARCHAR(20)  NOT NULL,
    reveal_mode       VARCHAR(20)  NOT NULL,
    title             VARCHAR(250) NOT NULL,
    source_exam_id    BIGINT       NULL,
    source_subject_id BIGINT       NULL,
    question_count    INT          NOT NULL,
    correct_count     INT          NOT NULL,
    score             DECIMAL(4,1) NOT NULL,
    started_at        DATETIME(3)  NOT NULL,
    finished_at       DATETIME(3)  NOT NULL,
    duration_ms       BIGINT       NOT NULL,
    PRIMARY KEY (id),
    KEY idx_practice_user (user_id, finished_at),
    CONSTRAINT fk_practice_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 每題的作答。question_snapshot 保存作答當時的題目（含正確答案與解說，JSON），
-- 題目之後被修改或刪除時歷史紀錄不受影響；question_id 在題目刪除後變成 NULL。
CREATE TABLE IF NOT EXISTS answer_records (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    record_id         BIGINT       NOT NULL,
    position          INT          NOT NULL,
    question_id       BIGINT       NULL,
    question_snapshot MEDIUMTEXT   NOT NULL,
    answer            TEXT         NOT NULL,
    score             DECIMAL(5,4) NOT NULL,
    is_correct        TINYINT(1)   NOT NULL,
    answered_at       DATETIME(3)  NULL,
    PRIMARY KEY (id),
    KEY idx_answers_record (record_id, position),
    CONSTRAINT fk_answers_record FOREIGN KEY (record_id) REFERENCES practice_records (id) ON DELETE CASCADE,
    CONSTRAINT fk_answers_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE SET NULL
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- 錯題本：沒有全對的題目（含部分給分與未作答）加入，再錯一次就累計次數；只能由使用者手動移除。
CREATE TABLE IF NOT EXISTS wrong_questions (
    user_id        BIGINT      NOT NULL,
    question_id    BIGINT      NOT NULL,
    wrong_count    INT         NOT NULL,
    first_wrong_at DATETIME(3) NOT NULL,
    last_wrong_at  DATETIME(3) NOT NULL,
    PRIMARY KEY (user_id, question_id),
    KEY idx_wrong_last (user_id, last_wrong_at),
    CONSTRAINT fk_wrong_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_wrong_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
