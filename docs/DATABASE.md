# 資料庫設定（MariaDB）

伺服器從第二階段起需要資料庫才能啟動。執行一般測試（`mvn test`）**不需要**資料庫。

本專案使用 **MariaDB**（本機為 MariaDB 13.0，服務名稱 `MariaDB`，埠號 3306），驅動為 MariaDB Connector/J。
`db/schema.sql` 只使用 MariaDB 與 MySQL 8 共通的語法，見 DECISIONS.md D-016。
`db/migrations/` 中的升級檔使用 MariaDB 的 `IF NOT EXISTS` 語法，可以重複執行。

以下指令在 PowerShell 中、於專案根目錄執行。

> **本機狀態（2026-11-02，第八階段）：** 第 1–4 步已完成。`exam_battle`、`exam_battle_test` 資料庫、`exam_battle` 帳號、
> 全部資料表（users、subjects、exams、questions、question_options、exam_questions、practice_records、answer_records、wrong_questions、
> question_groups）與初始大分類都已建立；兩個資料庫都已執行 `db/migrations/001_question_groups.sql`、`002_visibility_admin.sql` 與 `003_images.sql`（images 資料表）；目前沒有管理員帳號。
> `config/db.properties` 已產生（帳號密碼為隨機產生，只存在該檔案中）。換電腦或重建資料庫時再照以下步驟做。

## 1. 確認 MariaDB 服務

```powershell
Get-Service MariaDB
```

狀態不是 `Running` 時，以**系統管理員**身分執行：

```powershell
Start-Service MariaDB
```

舊的 `MySQL80` 服務不需要啟動；兩者都使用 3306 埠，不能同時執行。

## 2. 建立資料庫與專用帳號

`mariadb.exe` 不在 PATH 中，請用完整路徑以 root 登入（會詢問 root 密碼）：

```powershell
& "C:\Program Files\MariaDB 13.0\bin\mariadb.exe" -u root -p
```

在 `MariaDB>` 提示中執行。請把 `你的密碼` 換成自己設定的密碼：

```sql
CREATE DATABASE IF NOT EXISTS exam_battle      CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS exam_battle_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'exam_battle'@'localhost' IDENTIFIED BY '你的密碼';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX ON exam_battle.*      TO 'exam_battle'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX ON exam_battle_test.* TO 'exam_battle'@'localhost';
EXIT;
```

- 伺服器只使用 `exam_battle` 這個帳號，不使用 root。
- `exam_battle_test` 只給整合測試使用，與正式資料分開。
- 字元集指定為 `utf8mb4_unicode_ci`：不分大小寫，所以 `alice` 與 `ALICE` 視為同一帳號。不使用 MySQL 專用的 `utf8mb4_0900_ai_ci`。

## 3. 建立資料表與初始大分類

```powershell
Get-Content db\schema.sql -Raw -Encoding UTF8 | & "C:\Program Files\MariaDB 13.0\bin\mariadb.exe" --default-character-set=utf8mb4 -u exam_battle -p exam_battle
```

```powershell
Get-Content db\schema.sql -Raw -Encoding UTF8 | & "C:\Program Files\MariaDB 13.0\bin\mariadb.exe" --default-character-set=utf8mb4 -u exam_battle -p exam_battle_test
```

初始大分類（數學、英文、國文、電腦科學、自然科學、社會科學、證照考試、其他）：

```powershell
Get-Content db\seed_subjects.sql -Raw -Encoding UTF8 | & "C:\Program Files\MariaDB 13.0\bin\mariadb.exe" --default-character-set=utf8mb4 -u exam_battle -p exam_battle
```

`schema.sql` 與 `seed_subjects.sql` 都可重複執行，不會刪除既有資料。新安裝只需要執行 `schema.sql`，它已包含最新的資料表與欄位。

### 升級既有資料庫

`schema.sql` 只會建立不存在的資料表，**不會替既有的資料表加上新欄位**。從舊版升級時，請依序執行 `db/migrations/` 中尚未執行的檔案：

| 檔案 | 內容 | 需要執行的情況 |
|---|---|---|
| `001_question_groups.sql` | 新增 `question_groups` 資料表、`questions.group_id` 欄位（題組，D-034） | 資料庫在題組支援之前建立 |
| `002_visibility_admin.sql` | 新增 `exams.visibility`、`exams.taken_down`、`users.is_admin`（第八階段，D-039、D-040）；既有考卷維持公開 | 資料庫在第八階段之前建立 |
| `003_images.sql` | 新增 `images` 資料表，以及 `questions`、`question_options`、`question_groups` 的 `image_id` 欄位（題目圖片，D-043） | 資料庫在題目圖片支援之前建立 |

```powershell
Get-Content db\migrations\001_question_groups.sql -Raw -Encoding UTF8 | & "C:\Program Files\MariaDB 13.0\bin\mariadb.exe" --default-character-set=utf8mb4 -u exam_battle -p exam_battle
Get-Content db\migrations\001_question_groups.sql -Raw -Encoding UTF8 | & "C:\Program Files\MariaDB 13.0\bin\mariadb.exe" --default-character-set=utf8mb4 -u exam_battle -p exam_battle_test
```

執行 `002_visibility_admin.sql`、`003_images.sql` 的方式相同（把檔名換掉）。升級檔可以重複執行。伺服器啟動時會檢查，缺少時顯示要執行哪個檔案。

### 設定管理員

管理員可以下架別人的公開考卷（D-040）。程式中沒有授權介面，請用 root 或 `exam_battle` 帳號執行 SQL：

```sql
UPDATE users SET is_admin = 1 WHERE username = '帳號';   -- 設為管理員
UPDATE users SET is_admin = 0 WHERE username = '帳號';   -- 取消
```

使用者重新登入後生效（畫面上方會顯示【管理員】）。
匯入中文資料時務必加上 `--default-character-set=utf8mb4`（以及 PowerShell 的 `-Encoding UTF8`），否則中文可能變成亂碼。

## 4. 設定伺服器連線

複製範本並填入第 2 步設定的密碼：

```powershell
Copy-Item config\db.properties.example config\db.properties
notepad config\db.properties
```

```properties
db.url=jdbc:mariadb://localhost:3306/exam_battle
db.user=exam_battle
db.password=你的密碼
```

`config/db.properties` 已列在 `.gitignore`，不會進入版本控制。**不要把任何密碼寫進其他檔案或文件。**
也可以改用環境變數 `EXAMBATTLE_DB_URL`、`EXAMBATTLE_DB_USER`、`EXAMBATTLE_DB_PASSWORD`；有設定時優先於設定檔。

啟動伺服器時會檢查連線與 `users` 資料表，有問題會印出原因並結束。

## 5.（選用）執行資料庫整合測試

`JdbcUserDaoTest`、`JdbcBankDaoTest`、`JdbcPracticeDaoTest` 只在設定下列環境變數時執行，只會刪除自己建立的資料：

```powershell
$env:EXAMBATTLE_TEST_DB_URL = 'jdbc:mariadb://localhost:3306/exam_battle_test'
$env:EXAMBATTLE_TEST_DB_USER = 'exam_battle'
$env:EXAMBATTLE_TEST_DB_PASSWORD = Read-Host '測試資料庫密碼'
mvn -pl server -am test
```

預期：`JdbcUserDaoTest` 3 個、`JdbcBankDaoTest` 4 個、`JdbcPracticeDaoTest` 5 個測試通過（未設定時顯示為略過）。

## 常見問題

| 訊息 | 原因 |
|---|---|
| `Could not connect to address=(host=localhost)(port=3306)` | MariaDB 服務未啟動，或埠號不是 3306 |
| `Access denied for user 'exam_battle'@'localhost'` | 密碼錯誤，或第 2 步的帳號未建立 |
| `找不到 xxx 資料表` | 尚未執行第 3 步，或升級後未執行 `db/migrations/` 中的檔案 |
| `questions 資料表缺少 group_id 欄位` | 舊版資料庫，請執行 `db/migrations/001_question_groups.sql`（第 3 節「升級既有資料庫」） |
| `exams 資料表缺少 visibility 欄位`、`users 資料表缺少 is_admin 欄位` | 請執行 `db/migrations/002_visibility_admin.sql` |
| `找不到 images 資料表`、`question_options 資料表缺少 image_id 欄位` | 請執行 `db/migrations/003_images.sql` |
| `Unknown collation: 'utf8mb4_0900_ai_ci'` | 使用了舊版 schema；請改用目前的 `db/schema.sql` |
