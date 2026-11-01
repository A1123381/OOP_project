# 決策紀錄

格式：編號、日期、狀態（已確認 / 草案 / 已取代）、決定、理由、影響範圍。擁有者：architect。

---

## D-001 程式語言：Java 21
- 日期：2026-11-01　狀態：已確認
- 決定：使用 Java 21（LTS），父 POM 設定 `maven.compiler.release=21`。
- 理由：需求第 4 節建議 Java；21 為 LTS，可使用 record、switch pattern、virtual threads。
- 影響：全部模組。開發機需 JDK 21 以上（較新的 JDK 會以 `--release 21` 編譯）。

## D-002 建置工具：Maven 多模組
- 日期：2026-11-01　狀態：已確認
- 決定：父專案 `com.exambattle:exam-battle`，子模組 `common`、`server`、`client`、`tools`。
  依賴版本集中在父 POM 的 `dependencyManagement`（Jackson BOM、JUnit BOM）。
- 理由：共用協定與模型放在 `common`，避免 server 與 client 各自定義而不同步。
- 影響：`ocr` 模組於第六階段再加入；DAO 先放在 `server` 的 `com.exambattle.server.dao` 套件（見 D-006）。

## D-003 JSON 函式庫：Jackson
- 日期：2026-11-01　狀態：已確認
- 決定：使用 Jackson（`jackson-databind`），由 `common` 引入並提供序列化工具。
- 理由：需求第 4 節指定 JSON 傳輸；Jackson 成熟且支援 record。
- 影響：`common`，間接影響 server / client / tools。

## D-004 第一階段客戶端：主控台版
- 日期：2026-11-01　狀態：已確認
- 決定：第一階段的 client 為主控台程式；網路 I/O 與介面分離。
- 理由：先驗證通訊與多客戶端連線；GUI（JavaFX / Swing）或網頁版仍是需求第 9 節的待確認事項。
- 影響：`client`。確認 GUI 選型前不引入 GUI 依賴。

## D-005 通訊協定：一行一個 JSON
- 日期：2026-11-01　狀態：已確認（v0.2，第一階段實作）
- 決定：TCP Socket、UTF-8、每行一個 JSON 物件 `{type, requestId, payload}`。
  第一階段訊息：HELLO、WELCOME、PING、PONG、CHAT、CLIENT_JOINED、CLIENT_LEFT、ERROR、BYE。
- 理由：以行分隔易於用 `BufferedReader.readLine()` 處理，也方便用 telnet / nc 手動測試；
  `requestId` 讓請求與回應可以對應。
- 影響：詳見 [PROTOCOL.md](PROTOCOL.md)。v0.2 定案：埠號 5555、單行上限 65536 字元、客戶端每 15 秒 PING、伺服器 45 秒逾時，新增錯誤碼 `ALREADY_WELCOMED`。

## D-006 Subagent 分工與目錄擁有權
- 日期：2026-11-01　狀態：已確認
- 決定：`.claude/agents/` 下六個 subagent，各自只能修改自己擁有的路徑：

  | agent | 可修改路徑 |
  |---|---|
  | architect | `docs/`、`common/`、根 `pom.xml`（指派時含 `tools/`） |
  | server-dev | `server/`（不含 `dao` 套件） |
  | client-dev | `client/` |
  | db-dev | `db/schema.sql`、`server/src/{main,test}/java/com/exambattle/server/dao/` |
  | ocr-dev | `ocr/`（第六階段前不啟用） |
  | qa-reviewer | 無（唯讀，只能執行建置與測試） |

- 理由：避免多個 agent 同時修改同一處；協定與模型的變更集中由 architect 把關。
- 影響：擁有權目前以 agent 提示詞約束；qa-reviewer 另以工具清單（不含 Edit / Write）限制。

## D-007 基礎套件名稱
- 日期：2026-11-01　狀態：已確認
- 決定：`com.exambattle.<module>`，例如 `com.exambattle.server`。
- 影響：全部模組。

## D-008 伺服器並行模型
- 日期：2026-11-01　狀態：已確認
- 決定：
  - 每條連線一條 virtual thread 負責讀取。
  - 每個客戶端有自己的送出佇列與寫入執行緒，`send()` 只把訊息放進佇列，不會因單一慢速客戶端阻塞廣播。
  - 加入、離開、聊天廣播都在 `ClientRegistry` 的同一把鎖內完成，所有客戶端看到的事件順序一致。
  - 時間戳（`serverTime`）由伺服器產生。
- 理由：需求 3.6 要求伺服器統一控制並同步狀態；Java 21 virtual threads 讓每連線一執行緒的寫法簡單且可擴充。
- 已知限制：送出佇列目前沒有上限，第八階段穩定性測試時再評估背壓（backpressure）。
- 影響：`server`。

## D-009 可執行 jar
- 日期：2026-11-01　狀態：已確認
- 決定：`server`、`client`、`tools` 以 maven-shade-plugin 打包成含依賴的可執行 jar（`java -jar <module>/target/<module>-<version>.jar`）。
- 理由：主控台測試多個客戶端時不需處理 classpath。
- 影響：根 POM 的 pluginManagement 與三個模組的 POM。

## D-010 提前導入資料庫（第二階段）
- 日期：2026-11-01　狀態：已確認（使用者選擇）
- 決定：第二階段就建立 `users` 資料表與 `UserDao` / `JdbcUserDao`，帳號持久保存。資料庫產品與驅動見 D-016（原為 MySQL + mysql-connector-j，已改為 MariaDB）。
  其餘資料表（subjects、exams、questions…）仍於第三階段建立；房間只存在記憶體，等第四、五階段要保存競賽紀錄時再建 rooms / room_players。
- 設定：`config/db.properties`（不進版本控制）或環境變數 `EXAMBATTLE_DB_URL` / `_USER` / `_PASSWORD`；伺服器啟動時驗證連線與 users 資料表，失敗即結束。
- 測試：伺服器測試使用 `InMemoryUserDao`（test-jar），不需資料庫；`JdbcUserDaoTest` 只在設定 `EXAMBATTLE_TEST_DB_URL` 時執行。
- 影響：`db/schema.sql`、`server/.../dao/`、根 POM、`docs/DATABASE.md`。

## D-011 密碼雜湊
- 日期：2026-11-01　狀態：已確認
- 決定：JDK 內建 PBKDF2WithHmacSHA256，16 位元組隨機 salt，210,000 次迭代，256 位元輸出；迭代次數與 salt 一起存入資料庫，日後可調整。
  以 `MessageDigest.isEqual` 比對；帳號不存在時也做一次雜湊，避免以回應時間探測帳號。
- 理由：不需額外依賴，符合 OWASP 建議。
- 影響：`server/.../auth/`。

## D-012 房間規則
- 日期：2026-11-01　狀態：已確認（使用者選擇，定案需求第 9 節「房主權限」的部分內容）
- 決定：
  - 建立者為房主。**房主離開或斷線即解散房間**，其他玩家收到 `ROOM_CLOSED` 並回到大廳。
  - 暫不提供踢除玩家；`LeaveReason.KICKED` 保留未使用。
  - 每人同時只在一個房間；預設上限 50 人。
  - 房間代碼 6 碼，取自去除 0/O、1/I 的 32 個字元，以 SecureRandom 產生並保證不重複，加入時不分大小寫。
- 影響：`server/.../room/`、PROTOCOL.md v0.3。

## D-013 聊天範圍
- 日期：2026-11-01　狀態：已確認（使用者選擇）
- 決定：在房間內的聊天只送給同房玩家；在大廳的聊天只送給不在任何房間的已登入者。`CHAT` 廣播加入 `roomCode`（大廳為 null）。
  判斷所在位置與廣播在同一把鎖內完成。
- 影響：`server/.../handler/ChatHandler`。

## D-014 已知限制（第二階段）
- 日期：2026-11-01　狀態：待處理
- **密碼以明文經 TCP 傳送**：目前只適合在受信任的網路使用，預計第八階段導入 TLS（SSLSocket）。
- 尚無登入失敗次數限制與帳號鎖定，預計第八階段處理。
- `Database` 每次操作開新連線、未使用連線池；人數增加後再評估 HikariCP。
- 伺服器處理 LOGIN / REGISTER 時，資料庫查詢與 PBKDF2 在該連線的 virtual thread 上執行，不持有共用鎖，不影響其他連線。

## D-015 協定 v0.3
- 日期：2026-11-01　狀態：已確認
- 決定：HELLO 不再帶名稱，身分改由 LOGIN 取得；新增帳號與房間訊息共 13 種；`ROOM_PLAYER_LEFT` 帶完整 `player` 資訊（方便客戶端顯示，不必自行維護名單）。
- 影響：與 v0.2 客戶端不相容。詳見 [PROTOCOL.md](PROTOCOL.md)。

## D-016 資料庫改用 MariaDB
- 日期：2026-11-01　狀態：已確認（使用者選擇）
- 決定：
  - 資料庫由 MySQL 改為 **MariaDB**（本機 13.0，服務 `MariaDB`，埠號 3306）。
  - 驅動改為 `org.mariadb.jdbc:mariadb-java-client` 3.5.1，連線字串為 `jdbc:mariadb://`。
  - 加入 `org.slf4j:slf4j-jdk14`，讓驅動日誌走 java.util.logging。
  - 字元集排序改為 `utf8mb4_unicode_ci`（不分大小寫），取代 MySQL 專用的 `utf8mb4_0900_ai_ci`。
  - 唯一鍵衝突改以錯誤碼 1062 判斷，不依驅動丟出的例外子類別。
  - schema 只使用 MariaDB 與 MySQL 8 共通的語法，日後若要換回 MySQL，只需更換驅動與連線字串。
- 理由：使用者環境改用 MariaDB。MariaDB 與需求第 4 節建議的 MySQL 相容，JDBC 與 DAO 架構不變。
- 安全：伺服器只用權限受限的 `exam_battle` 帳號，不用 root；所有密碼只存在 `config/db.properties`（不進版本控制）或環境變數，不寫進任何文件。
- 影響：根 POM、server POM、`db/schema.sql`、`config/db.properties.example`、`JdbcUserDao`、`docs/DATABASE.md`。

## D-017 GUI 採用 JavaFX，整個客戶端改為圖形介面
- 日期：2026-11-01　狀態：已確認（使用者選擇，定案需求第 9 節「JavaFX 桌面版或網頁版」）
- 決定：
  - 使用 OpenJFX 21.0.5（LTS，配合 Java 21）的 `javafx-controls`，畫面以 Java 程式碼建構，不使用 FXML。
  - 登入／註冊、大廳、房間、聊天、題庫全部做成視窗。主控台客戶端保留，當作測試與除錯工具（`java -cp client.jar com.exambattle.client.ClientMain`）。
  - 狀態只依伺服器訊息更新（`ClientState`）；請求以 `ServerConnection.request()` 的 future 依 requestId 對應回應。
  - 表單驗證放在不依賴 JavaFX 的 `QuestionDraft`、`ExamDraft`，可單元測試；規則與伺服器相同，最終以伺服器為準。
- 已知限制：
  - shade 打包會含建置平台的 JavaFX 原生檔，`client` jar 只能在相同作業系統執行（目前為 Windows）。
  - 在 JDK 24 以上執行時，JavaFX 21 會印出 `sun.misc.Unsafe` 與「Unsupported JavaFX configuration」警告，不影響功能；要消除需升級到需要較新 JDK 的 JavaFX 版本。
- 影響：`client/`、根 POM。

## D-018 題庫權限
- 日期：2026-11-01　狀態：已確認（使用者選擇）
- 決定：所有登入者都能瀏覽全部考卷；只有建立者能修改或刪除考卷與其題目（`NOT_OWNER`）。
  **正確答案只提供給建立者**，其他人只看得到「是否已設定答案」，避免之後的練習與競賽作弊。
  公開／私人題庫與管理員審核留到第八階段（需求第 9 節仍待確認）。
- 影響：`server/.../bank/ExamService`、PROTOCOL.md 3.5。

## D-019 大分類
- 日期：2026-11-01　狀態：已確認（使用者選擇）
- 決定：只有一層大分類，全站共用，名稱不分大小寫唯一，任何登入者都可新增。
  初始清單放在 `db/seed_subjects.sql`（資料，不是程式）。`subjects.parent_id` 保留給之後的子分類。
- 影響：`db/`、`SubjectService`。

## D-020 題型範圍與答案規則
- 日期：2026-11-01　狀態：已確認（使用者選擇）
- 決定：第三階段只做單選、多選、是非題；填充、簡答、問答延後（需要人工或其他判斷方式，不能用 AI 判答）。
  允許「答案未設定」（需求 3.4：不得推測無法確認的答案），以 `unansweredCount` 與介面警示標示，之後的練習與競賽不使用這類題目。
  單選題最多一個正確選項；選擇題 2–10 個選項。
- 影響：`BankValidator`、`QuestionDraft`、schema。

## D-021 考卷與題目分開管理
- 日期：2026-11-01　狀態：已確認
- 決定：題目存在 `questions`，經由 `exam_questions(exam_id, question_id, position)` 放進考卷，日後同一題可重複用於多份考卷（需求第 6 節）。
  目前介面只在考卷內新增題目；移除或刪除考卷時，不再屬於任何考卷的題目會一併刪除。
  `position` 不設唯一鍵，重新排序時不會暫時衝突，讀取時依 `(position, question_id)` 排序。
  題目與選項的寫入、刪除都在同一個交易內完成（`Database.inTransaction`）。
- 影響：`db/schema.sql`、`server/.../dao/`。

## D-022 階段順序調整：先做第五階段（單人），第四階段延後
- 日期：2026-11-01　狀態：已確認（使用者選擇）
- 決定：完成第三階段後直接進入第五階段（考試紀錄、個人成績、錯題本、錯題複習），並加入需求 3.5 的單人練習作為紀錄的來源。
  第四階段（多人競賽：同步出題、倒數計時、即時排行榜）延後，之後再回來做。
- 影響：第四階段實作時，`game_records` 等多人競賽資料表另外建立；單人練習的計分規則（D-023）可沿用到競賽的判分。

## D-023 單人練習的計分規則
- 日期：2026-11-01　狀態：已確認（使用者選擇多選題依學測規則）
- 決定：
  - 單選、是非：答對 1 分，否則 0 分。
  - 多選：n 個選項中有 k 個與正確答案不一致，得 max(0, (n − 2k) / n) 分；未作答 0 分。
  - 只有全對算「答對」。
  - 總分為各題得分平均 × 100，四捨五入到小數 1 位。
  - 只出已設定答案的題目。
- 答案揭曉：開始時由使用者選擇考試模式（交卷後一次批改，可前後修改）或練習模式（每題立即批改，不能修改）。
- 計分與計時由伺服器負責（`server/.../practice/Grader`），開始練習時不傳送答案。
- 本階段不設時間限制。

## D-024 錯題本規則
- 日期：2026-11-01　狀態：已確認（使用者選擇只能手動移除）
- 決定：沒有全對的題目（含部分給分、未作答）在交卷時加入錯題本；已在錯題本中的題目累計錯誤次數。
  **答對不會自動移除，只能由使用者手動移除。**題目被刪除時，錯題本中的該題一併消失（ON DELETE CASCADE）。
  錯題本顯示正確答案與解說（使用者交卷後已經看過，不違反 D-018）。
- 影響：`wrong_questions` 資料表、`PracticeService`、GUI 錯題本分頁。

## D-025 進行中的練習與紀錄快照
- 日期：2026-11-01　狀態：已確認
- 決定：
  - **進行中的練習**只存在伺服器記憶體，每人同時一個，開始新的會取代舊的；斷線時清除。
  - **紀錄寫入**：交卷時在同一個交易內寫入 `practice_records`、`answer_records`，並更新 `wrong_questions`。
  - **題目快照**：每題作答保存當時的題目快照（JSON，含正確答案與解說）。題目之後被修改或刪除，歷史紀錄仍保持原樣；`answer_records.question_id` 改為 NULL。
  - **時間欄位**：一律以 UTC 寫入 `DATETIME(3)`，避免 `TIMESTAMP` 的自動更新與時區轉換。
  - **驅動限制**：MariaDB 驅動的批次協定不支援 `INSERT … SELECT … ON DUPLICATE KEY`，錯題的新增或累計改為逐筆執行。
- 已知限制：斷線或伺服器重啟時，尚未交卷的練習會遺失；之後若需要「中斷後繼續」，再把進行中的練習寫入資料庫。
- 影響：`db/schema.sql`、`server/.../dao/JdbcPracticeDao`、`server/.../practice/`。

## D-026 OCR 在客戶端本機執行，新增 `ocr` 模組
- 日期：2026-11-01　狀態：已確認（使用者選擇客戶端本機）
- 決定：
  - 新增 Maven 模組 `ocr`（`com.exambattle.ocr`），由 client 依賴。
  - 以 Apache PDFBox 3.0.3 擷取 PDF 文字層與轉影像，以 Tess4J 5.13.0（內含 Windows 版 Tesseract 5 原生檔）辨識影像。
  - PDF 逐頁處理：文字層有 20 個以上非空白字元就直接使用，否則轉成 200 DPI 影像再 OCR；使用者也可以勾選「強制 OCR」。
  - 辨識時設定 `user_defined_dpi=300`、`preserve_interword_spaces=1`，並移除中文字之間殘留的空白。
- 理由：
  - 實測在沒有解析度資訊時，Tesseract 會假設很低的 DPI，中文辨識嚴重錯誤。
  - 以 300 DPI 放大掃描影像反而比 200 DPI 差，因此 PDF 掃描頁採用 200 DPI。
- 影響：client jar 約增加至 33MB；伺服器不變。

## D-027 原始檔不上傳、不保存；草稿只存本機（資訊安全）
- 日期：2026-11-01　狀態：已確認（使用者選擇資訊安全較高的方案）
- 決定：
  - 原始 PDF／圖片只在使用者電腦上讀取，**不上傳、不複製、不保存**；伺服器、資料庫與協定在第六階段都沒有變動，沒有新的攻擊面。
  - 辨識結果可存成本機草稿檔（`*.ebdraft.json`），只含原檔名（不含路徑）、各頁文字與題目草稿。
  - 第七階段校正後，才經由既有的 `ADD_QUESTION` 等訊息匯入題庫。
- 影響：`ocr/`、`client/.../importer/ImportDraftFile`、GUI「匯入」分頁。

## D-028 切題規則與「不推測答案」
- 日期：2026-11-01　狀態：已確認
- 決定：`QuestionSplitter` 只用格式比對（不使用 AI）：
  - **題號**：`1.`、`1、`、`1)`、`(1)`、`第1題`。題號必須遞增，避免誤判內文；跳號時加上警示。
  - **選項**：`(A)`、`A.`、`A、`、`A)`（A–J）。OCR 損壞的記號（少括號、(C) 被讀成 (0／(O）只在字母剛好是下一個預期字母時接受，並加上警示。
  - **題型推測**：分段標題（單選／多選／是非）決定題型；有選項時預設為單選；無法判斷時留空。
  - 不推測正確答案，題目中的「答案：B」之類的文字一律當成內容。所有結果都是待第七階段人工校正的草稿。

## D-029 OCR 語言資料
- 日期：2026-11-01　狀態：已確認（使用者選擇繁中＋英文 best 版）
- 決定：使用 `tessdata_best` 的 `chi_tra`、`eng`（共約 28MB），從 GitHub tesseract-ocr 官方倉庫下載到專案根目錄的 `tessdata/`（不進版本控制）。
  位置可用系統屬性 `exambattle.tessdata` 或環境變數 `EXAMBATTLE_TESSDATA` 指定，設定方式見 docs/OCR.md。
- 已知限制：直排（`chi_tra_vert` 未下載）、數學公式、表格、手寫、簡體中文。

## D-030 答案來源：手動＋答案卷（定案需求第 9 節）
- 日期：2026-11-01　狀態：已確認（使用者選擇）
- 決定：校正時可以逐題手動設定答案，也可以匯入答案卷：本機擷取／OCR 的 PDF、圖片，或直接貼上文字。
  - `AnswerKeyParser`（ocr 模組）支援 `1.B 2.AC 3.○ 4.×`、`1 (B)`、表格兩行式（題號／答案）。
  - 無法解析的片段列為警告；同一題出現兩次時使用後者並警告。
  - `AnswerKeyApplier` 依題號套用，只套用能明確對應的答案。題型未選、單選卻有多個字母、字母超出選項數、是非題不是對／錯記號時一律不套用並說明原因。
  - 記號「F」同時可能是選項 F 或「錯」，依題型解讀。
  - 套用前先顯示預覽，使用者可以先修改文字再套用。
- 實測：答案卷 OCR 可能把 ○ 讀成 0；這類記號不會被猜測，會在預覽中標示「無法解析」。
- 編輯元件抽出為 `QuestionEditor`，題庫對話框與匯入校正共用。

## D-031 整批匯入：單一請求、單一交易
- 日期：2026-11-01　狀態：已確認（使用者選擇「失敗就全部不匯入」）
- 決定：
  - 新增 `IMPORT_QUESTIONS`。伺服器先驗證全部題目與目標考卷（新考卷或自己的考卷），再由 `JdbcImportDao` 在同一個交易內建立考卷並寫入全部題目。
  - 一次最多 200 題；答案可以未設定（D-020）。
  - 單行上限由 65,536 調高為 1,048,576 字元，讓整批匯入能放進一行；客戶端在送出前先檢查長度。
- 已知影響：每條連線最多暫存約 1M 字元的單行；第八階段穩定性測試時再評估。

## D-032 原圖對照只在本機即時轉出
- 日期：2026-11-01　狀態：已確認（使用者選擇）
- 決定：校正時右側顯示題目來源頁的圖片，由 `PageImages` 從使用者選擇的本機檔案即時轉出（110 DPI），只存在記憶體。
  不寫入草稿、不上傳。從草稿接續時需要再選一次原檔；檔名與草稿記錄不同時會提醒。

## D-033 匯入草稿 v2
- 日期：2026-11-01　狀態：已確認
- 決定：草稿檔改存校正項目 `items`（是否匯入、是否已匯入、題號、來源頁、原始警示、題型、內容、選項與正確答案、是非答案、解說）。
  仍然只存原檔名，不存路徑或圖片。讀取第六階段的 v1 草稿時，自動把切題結果轉成校正項目。
