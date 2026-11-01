# Exam Battle — 專案規則

不限定科目的考試題庫與多人即時競賽練習系統。完整需求見 [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md)，
通訊協定見 [docs/PROTOCOL.md](docs/PROTOCOL.md)，已確認的決策見 [docs/DECISIONS.md](docs/DECISIONS.md)。

## 目前階段

- 第一階段：基本通訊，已驗收。
- 第二階段：使用者及房間，已驗收。
- 第三階段：基本題庫 + JavaFX 客戶端，已驗收。
- 第四階段：多人競賽，**延後**（使用者決定先做單人功能，D-022）。
- 第五階段：單人練習、紀錄、個人成績、錯題本，已驗收。
- 第六階段：考卷匯入，已驗收。
- **第七階段：OCR 校正與匯入題庫，已實作，等待使用者驗收。** 內容包括：
  - 校正編輯（共用 `QuestionEditor`）、原圖對照（本機即時轉出，D-032）
  - 答案：手動＋答案卷（貼上文字或本機 OCR），只套用能明確對應的答案（D-030）
  - `IMPORT_QUESTIONS` 整批匯入到新考卷或自己的考卷，單一交易（D-031）；單行上限調為 1M 字元
  - 草稿 v2（D-033）；協定 v0.6

第六階段內容：
  - 新增 `ocr` 模組（PDFBox 文字層＋Tess4J OCR，繁中＋英文），在客戶端本機執行（D-026）
  - 原始檔不上傳不保存，伺服器、資料庫、協定都沒有變動（D-027）
  - 初步切題（題號、選項、分段推測題型，不推測答案，D-028）；本機草稿檔 `*.ebdraft.json`
  - GUI 新增「匯入」分頁；語言資料見 docs/OCR.md（D-029）

第五階段內容：
  - 練習模式：完整考卷、隨機抽題、錯題複習；考試模式／練習模式（D-023）
  - 伺服器計分（多選依學測規則部分給分），開始時不傳答案
  - 紀錄（含題目快照）、個人統計、錯題本（只能手動移除，D-024、D-025）
  - GUI 新增「練習」「紀錄」「錯題本」分頁；協定 v0.5

第三階段內容：
  - 題庫資料表、初始大分類（`db/seed_subjects.sql`）
  - 考卷與單選／多選／是非題的新增、編輯、刪除、排序；只有建立者能修改，答案只給建立者（D-018、D-020）
  - JavaFX 客戶端：登入、大廳、房間、聊天、題庫（D-017）；主控台客戶端保留為測試工具
  - 協定 v0.4

第二階段內容：
  - MariaDB users 資料表與 DAO（提前導入，見 D-010、D-016）
  - 註冊與登入（PBKDF2）
  - 建立、加入、離開房間；房主離開即解散
  - 大廳與房間分開的聊天
  - 協定 v0.3

使用手冊見 [docs/MANUAL.md](docs/MANUAL.md)，測試方法見 [docs/TESTING.md](docs/TESTING.md)，資料庫設定見 [docs/DATABASE.md](docs/DATABASE.md)。
下一步由使用者決定（延後的第四階段多人競賽，或第八階段系統完善），須等使用者確認後才開始。

階段推進規則：每個階段完成後停下，提供測試方法與預期結果，等使用者確認再進入下一階段。
不得提前實作後續階段的功能。

**每個階段完成時都要更新 [docs/MANUAL.md](docs/MANUAL.md)**（給使用者看的操作手冊）：
- 加入新功能的操作說明。
- 更新開頭的「目前版本」與第 9 節「功能進度」。
- 補充常見問題。
- 手冊只寫使用者看得到的操作，協定與實作細節放在其他文件。

## 開發限制（需求第 2 節，不可違反）

1. 不整合 ChatGPT、Gemini、Claude 或其他 LLM API。
2. 不使用 AI 自動生成題目。
3. 不使用 AI 自動判斷答案。
4. 題目來源以使用者提供的歷屆考卷為主。
5. 可以使用 OCR 技術辨識考卷文字，但 OCR 不等於 AI 題目生成。
6. OCR 辨識結果必須提供人工檢查及修改功能。
7. 系統必須具有通用性，不可將科目或題目內容寫死在程式中。

## 工作規則（需求第 8 節）

1. 先理解整體架構，再開始撰寫程式碼。
2. 不要擅自刪除或更改已經確定的功能需求。
3. 不要自行加入 AI 題目生成或 LLM API。
4. 不要把系統限制在資訊工程或任何單一科目。
5. 所有題目內容都應該由使用者匯入或手動建立。
6. OCR 結果必須允許人工校正。
7. 程式碼應保持模組化，避免將所有功能寫在同一個類別。
8. 資料庫操作應使用 DAO 或其他適當的資料存取層。
9. 多人競賽的關鍵邏輯（計時、答案判斷、計分、排行榜）必須由伺服器控制。
10. 每次開發應先說明修改內容、涉及的檔案及實作方式。
11. 如果需要修改既有程式碼，應先理解原本程式架構，避免破壞已完成的功能。
12. 每個階段完成後，提供測試方法及預期結果。
13. 遇到需求不明確時，先提出問題，不要自行假設重要規格（見需求第 9 節的待確認事項）。
14. 不要只提供概念或偽程式碼；在要求實作時，應提供可整合、可執行的完整程式碼。

## 技術選擇（已確認）

- Java 21（`maven.compiler.release=21`）
- Maven 多模組：`common` / `server` / `client` / `tools`
- JSON 處理：Jackson
- 通訊：Java Socket，一行一個 JSON（見 PROTOCOL.md）
- 客戶端：JavaFX 21（OpenJFX，D-017）；主控台版保留為測試工具
- 資料庫（第二階段起）：MariaDB + JDBC（MariaDB Connector/J），經由 DAO 存取，schema 維持與 MySQL 8 相容（D-016）；連線設定放在 `config/db.properties`（不進版本控制）
- 密碼：PBKDF2WithHmacSHA256（JDK 內建），不保存明文
- 考卷匯入：Apache PDFBox 3.0.3、Tess4J 5.13.0（Tesseract 5），只在客戶端執行；語言資料放在 `tessdata/`（不進版本控制）

## 模組與目錄擁有權

| 路徑 | 內容 | 擁有者 subagent |
|---|---|---|
| `pom.xml`（根）、`common/`、`docs/` | 父 POM、共用模型與協定訊息、文件 | architect |
| `server/`（`dao` 套件除外） | Socket 伺服器、房間、遊戲、計分 | server-dev |
| `server/src/{main,test}/java/com/exambattle/server/dao/`、`db/`、`config/db.properties.example` | DAO、資料表結構、連線設定範本 | db-dev |
| `client/` | 客戶端（第一階段為主控台版） | client-dev |
| `ocr/` | PDF 文字擷取、OCR、切題（第六階段起） | ocr-dev |
| `tools/` | 開發與測試工具（如多客戶端連線測試） | architect 指派 |
| 全部（唯讀） | 審查與執行測試 | qa-reviewer |

每個 subagent 只能修改自己擁有的路徑；需要動到其他路徑時，回報給主對話由擁有者處理。
協定或共用模型的變更一律先改 `docs/PROTOCOL.md` 與 `common/`，並記錄於 `docs/DECISIONS.md`。

基礎套件名稱：`com.exambattle.<module>`。

## 建置與測試指令

需安裝 JDK 21 以上與 Maven 3.9 以上。執行伺服器需要 MariaDB（見 docs/DATABASE.md）；`mvn test` 不需要。

```bash
mvn -q validate          # 驗證 POM 結構
mvn -q compile           # 編譯全部模組
mvn test                 # 執行全部測試
mvn -pl server -am test  # 只測試 server 及其相依模組
mvn -q package           # 打包成可執行 jar
java -jar server/target/server-0.1.0-SNAPSHOT.jar [port]
java -jar client/target/client-0.1.0-SNAPSHOT.jar                     # JavaFX 客戶端
java -cp client/target/client-0.1.0-SNAPSHOT.jar com.exambattle.client.ClientMain [host] [port]   # 主控台客戶端
java -jar tools/target/tools-0.1.0-SNAPSHOT.jar [host] [port] [clients] [rooms] [messages]
```
