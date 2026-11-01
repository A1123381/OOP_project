# Exam Battle 通訊協定（v0.7）

狀態：**題組支援版**（第四階段多人競賽延後，見 DECISIONS.md D-022；第六階段沒有協定變動）。擁有者：architect。變更須同步記錄於 DECISIONS.md。

v0.7 相對 v0.6：新增題組（D-034）。`QuestionData` 與 `PracticeQuestion` 加上 `groupId`、`passage`；新增 `UPDATE_GROUP`／`GROUP_SAVED`（第 3.5 節）與錯誤碼 `GROUP_NOT_FOUND`；`IMPORT_QUESTIONS` 加上 `groups`、`groupIndexes`。新欄位都可以省略，舊的請求格式仍然有效。

v0.6 相對 v0.5：新增 `IMPORT_QUESTIONS`／`IMPORT_DONE`（第 3.7 節）與錯誤碼 `TOO_MANY_QUESTIONS`；單行上限由 65,536 調高為 1,048,576 字元。

v0.5 相對 v0.4：新增單人練習、紀錄、錯題本訊息（第 3.6 節）與 5 個錯誤碼，原有訊息不變。

v0.4 相對 v0.3：新增題庫訊息（第 3.5 節）與 5 個錯誤碼，原有訊息不變。

v0.3 相對 v0.2 的破壞性變更：
- `HELLO` 不再帶 `name`，只做握手；身分改由 `LOGIN` 取得。
- `WELCOME` 不再附在線名單（改由 `LOGIN_OK` 提供）。
- `CHAT`、`CLIENT_JOINED`、`CLIENT_LEFT` 改用使用者資訊 `UserInfo`。

## 1. 傳輸層

- TCP（Java Socket），文字串流，編碼 **UTF-8**。
- **一行一個 JSON 物件**，以 `\n` 結尾。JSON 內不得含未跳脫的換行（Jackson 預設輸出即符合）。
- 單行長度上限：1,048,576 個字元（不含換行；v0.6 起，為了整批匯入由 65,536 調高，見 D-031）。超過時伺服器回 `ERROR`（`LINE_TOO_LONG`）並關閉連線。
- 空白行會被忽略。
- 預設埠號：`5555`（可由啟動參數覆寫）。
- **注意：** 目前未加密，密碼以明文傳送，只適合在受信任的網路使用（見 DECISIONS.md D-014）。

## 2. 訊息格式

```json
{"type":"CHAT","requestId":"c-42","payload":{"text":"hello"}}
```

| 欄位 | 型別 | 必填 | 說明 |
|---|---|---|---|
| `type` | string | 是 | 訊息類型，大寫底線命名（見第 3 節）。 |
| `requestId` | string \| null | 否 | 由發起請求的一方產生，在該連線內唯一即可。回應訊息帶回相同的 `requestId`；伺服器主動推播的訊息為 `null`。 |
| `payload` | object | 是 | 依 `type` 而定；無內容時為 `{}`。 |

- 接收端應忽略 `payload` 中不認得的欄位（向前相容）。
- 不認得的 `type` 回 `ERROR`（`UNKNOWN_TYPE`），連線不中斷。
- 客戶端送出伺服器專用類型（如 `WELCOME`、`ROOM_STATE`）也視為 `UNKNOWN_TYPE`。

### 共用結構：UserInfo

```json
{"userId":12,"username":"alice","displayName":"Alice"}
```

`userId` 為資料庫中的使用者編號（整數）。

### 連線狀態

| 狀態 | 可送出 | 其他訊息的錯誤碼 |
|---|---|---|
| 剛連線 | `HELLO`、`PING`、`PONG`、`BYE` | `NOT_HELLO` |
| 已 WELCOME、未登入 | 上述 + `REGISTER`、`LOGIN` | `NOT_LOGGED_IN` |
| 已登入（大廳或房間） | 全部客戶端類型 | 依各訊息規則 |

## 3. 訊息類型

方向：C = Client，S = Server。

### 3.1 連線

#### HELLO（C → S）／WELCOME（S → C）
```json
{"type":"HELLO","requestId":"c-1","payload":{"protocolVersion":1}}
{"type":"WELCOME","requestId":"c-1","payload":{"clientId":"u-7","protocolVersion":1}}
```
- `protocolVersion` 選填，目前為 `1`；不相符時回 `INVALID_PAYLOAD`。
- 重複 HELLO 回 `ALREADY_WELCOMED`。
- `clientId`：本次連線的識別碼，只用於除錯與日誌。

#### PING／PONG（雙向）
任一方都可送出 `PING`，收到者必須回帶相同 `requestId` 的 `PONG`。

#### BYE（C → S，或 S → C）
客戶端送出表示要離開；伺服器處理完離開房間與 `CLIENT_LEFT` 後關閉連線。
伺服器關閉時對每個客戶端送出 `BYE {reason: "SERVER_SHUTDOWN"}`。

#### ERROR（S → C）
```json
{"type":"ERROR","requestId":"c-5","payload":{"code":"ROOM_NOT_FOUND","message":"找不到房間 ABC234"}}
```
`requestId` 為造成錯誤的請求；無法辨識時為 `null`。錯誤碼見第 4 節。

### 3.2 帳號

#### REGISTER（C → S）／REGISTER_OK（S → C）
```json
{"type":"REGISTER","requestId":"c-2","payload":{"username":"alice","password":"correct horse","displayName":"Alice"}}
{"type":"REGISTER_OK","requestId":"c-2","payload":{"userId":12,"username":"alice","displayName":"Alice"}}
```
- `username`：3–20 個英數字或底線，不分大小寫唯一。
- `password`：8–64 字元。伺服器以 PBKDF2 雜湊後儲存，不保存明文。
- `displayName`：去除前後空白後 1–32 字元，不可含控制字元，可與他人重複。
- 註冊不會自動登入。已登入的連線送出 REGISTER 回 `ALREADY_LOGGED_IN`。
- 帳號已存在回 `USERNAME_TAKEN`。

#### LOGIN（C → S）／LOGIN_OK（S → C）
```json
{"type":"LOGIN","requestId":"c-3","payload":{"username":"alice","password":"correct horse"}}
{"type":"LOGIN_OK","requestId":"c-3","payload":{"user":{"userId":12,"username":"alice","displayName":"Alice"},"online":[{"userId":5,"username":"bob","displayName":"Bob"}]}}
```
- 帳號不存在或密碼錯誤一律回 `INVALID_CREDENTIALS`，不透露是哪一項。
- 同一帳號已在其他連線登入，或本連線已登入，回 `ALREADY_LOGGED_IN`。
- `online`：其他已登入的使用者。
- 登入後位於**大廳**。

#### CLIENT_JOINED／CLIENT_LEFT（S → 其他已登入者）
```json
{"type":"CLIENT_JOINED","requestId":null,"payload":{"userId":12,"username":"alice","displayName":"Alice"}}
{"type":"CLIENT_LEFT","requestId":null,"payload":{"user":{"userId":12,"username":"alice","displayName":"Alice"},"reason":"BYE"}}
```
- 登入成功時送出 `CLIENT_JOINED`；已登入者離線時送出 `CLIENT_LEFT`。範圍為全伺服器，與所在房間無關。
- `reason`：`BYE` | `DISCONNECTED` | `TIMEOUT`（`KICKED` 保留未使用）。

### 3.3 房間

房間只存在伺服器記憶體中。每位使用者同時最多在一個房間；建立者即房主。

#### CREATE_ROOM（C → S）
```json
{"type":"CREATE_ROOM","requestId":"c-4","payload":{}}
```
成功時回 `ROOM_STATE`。已在房間內回 `ALREADY_IN_ROOM`。

#### JOIN_ROOM（C → S）
```json
{"type":"JOIN_ROOM","requestId":"c-5","payload":{"roomCode":"k7m2xq"}}
```
- `roomCode` 不分大小寫，前後空白會被去除。
- 成功時回 `ROOM_STATE`，並對房內其他人送 `ROOM_PLAYER_JOINED`。
- 錯誤：`ROOM_NOT_FOUND`、`ROOM_FULL`、`ALREADY_IN_ROOM`。

#### ROOM_STATE（S → C）
```json
{"type":"ROOM_STATE","requestId":"c-5","payload":{"roomCode":"K7M2XQ","ownerUserId":5,"maxPlayers":50,"players":[{"userId":5,"username":"bob","displayName":"Bob"},{"userId":12,"username":"alice","displayName":"Alice"}]}}
```
- `roomCode`：6 碼，字元取自 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789`，在進行中的房間內唯一。
- `players` 依加入順序排列。

#### GET_ROOM（C → S）
重新取得自己所在房間的 `ROOM_STATE`（顯示房間玩家）。不在房間回 `NOT_IN_ROOM`。

#### LEAVE_ROOM（C → S）／LEFT_ROOM（S → C）
```json
{"type":"LEAVE_ROOM","requestId":"c-6","payload":{}}
{"type":"LEFT_ROOM","requestId":"c-6","payload":{"roomCode":"K7M2XQ"}}
```
- 回到大廳。不在房間回 `NOT_IN_ROOM`。
- 一般玩家離開：房內其他人收到 `ROOM_PLAYER_LEFT`。
- 房主離開：房間解散，房內其他人收到 `ROOM_CLOSED`。

#### ROOM_PLAYER_JOINED／ROOM_PLAYER_LEFT（S → 房內其他人）
```json
{"type":"ROOM_PLAYER_JOINED","requestId":null,"payload":{"roomCode":"K7M2XQ","player":{"userId":12,"username":"alice","displayName":"Alice"}}}
{"type":"ROOM_PLAYER_LEFT","requestId":null,"payload":{"roomCode":"K7M2XQ","player":{"userId":12,"username":"alice","displayName":"Alice"},"reason":"LEFT"}}
```
`reason`：`LEFT`（送出 LEAVE_ROOM）| `DISCONNECTED`（斷線、逾時或 BYE）。

#### ROOM_CLOSED（S → 房內其他人）
```json
{"type":"ROOM_CLOSED","requestId":null,"payload":{"roomCode":"K7M2XQ","reason":"OWNER_LEFT"}}
```
- `reason`：`OWNER_LEFT` | `OWNER_DISCONNECTED`。
- 收到者自動回到大廳，房間代碼失效。

### 3.4 聊天

#### CHAT（C → S，S → 同範圍的人）
```json
{"type":"CHAT","requestId":"c-7","payload":{"text":"大家好"}}
{"type":"CHAT","requestId":null,"payload":{"fromUserId":12,"fromName":"Alice","text":"大家好","serverTime":"2026-11-01T12:00:00.000Z","roomCode":"K7M2XQ"}}
```
- 需已登入。`text` 為 1–1000 字元，不可全為空白。
- **範圍：** 在房間內只送給同房玩家；在大廳只送給大廳（不在任何房間）的已登入者。
- 發送者也會收到，帶回原 `requestId`；其他人為 `null`。
- `roomCode` 為 `null` 表示大廳聊天。`serverTime` 由伺服器填入，客戶端送來的值一律忽略。

### 3.5 題庫（v0.4，第三階段）

全部需要登入。所有登入者都能瀏覽；只有考卷建立者能修改或刪除（其他人收到 `NOT_OWNER`）。
第三階段只支援三種題型：`SINGLE_CHOICE`（單選）、`MULTIPLE_CHOICE`（多選）、`TRUE_FALSE`（是非）。

#### 共用結構

```json
SubjectInfo  {"id":1,"name":"數學"}
ExamSummary  {"id":7,"title":"113 學年度期中考","subject":{"id":1,"name":"數學"},"year":2024,"examType":"期中考",
              "owner":{"userId":12,"username":"teacher","displayName":"王老師"},"questionCount":4,"unansweredCount":1}
QuestionData {"id":31,"type":"SINGLE_CHOICE","content":"1 + 1 = ?","explanation":"基本加法",
              "options":[{"id":90,"content":"1","correct":false},{"id":91,"content":"2","correct":true}],
              "trueFalseAnswer":null,"answerSet":true,"groupId":null,"passage":null}
```

- `year`、`examType`、`explanation` 可為 `null`。
- **答案只給建立者**：其他人收到的 `options[].correct` 與 `trueFalseAnswer` 一律為 `null`；`answerSet`（是否已設定答案）所有人都看得到。
- 送出題目時 `id`、`answerSet` 會被忽略；選項的 `correct` 為 `null` 視為 `false`。

#### 題組（v0.7）

閱讀測驗、克漏字等多題共用一篇文章（D-034）。文章只存一份，所有人都看得到（文章不是答案）。

- 回應中：`groupId` 是所屬題組，`passage` 是題組文章；不屬於題組時兩者都是 `null`。
- `ADD_QUESTION`／`UPDATE_QUESTION` 的 `question`：
  - 帶 `groupId`：加入同一份考卷中既有的題組（`passage` 會被忽略）；不屬於這份考卷時回 `GROUP_NOT_FOUND`。
  - 只帶 `passage`：建立新題組。
  - 兩者都是 `null`：不屬於題組。`UPDATE_QUESTION` 可以用這個方式把題目移出題組。
- 文章 1–20,000 字元；克漏字的空格慣例寫成 `【11】`（客戶端以粗體顯示）。
- 題組不再有任何題目時自動刪除。

#### 答案規則

- 選擇題需要 2–10 個選項，選項內容不可空白（1–1000 字元）；是非題不可有選項。
- 單選題最多 1 個正確選項，多選題不限。
- **允許答案未設定**：選擇題沒有正確選項，或是非題 `trueFalseAnswer` 為 `null`。系統不推測答案；這類題目計入 `unansweredCount`，之後的練習與競賽不會使用。
- 題目內容 1–5000 字元、解說最多 5000 字元、考卷名稱 1–200 字元、考試類型最多 50 字元、年份 1900–2100、分類名稱 1–50 字元。

#### 訊息

| 請求 | payload | 成功回應 | 可能的錯誤 |
|---|---|---|---|
| `LIST_SUBJECTS` | `{}` | `SUBJECTS {subjects:[SubjectInfo]}`（依名稱排序） | — |
| `CREATE_SUBJECT` | `{name}` | `SUBJECT_SAVED` = SubjectInfo | `SUBJECT_EXISTS`（不分大小寫） |
| `LIST_EXAMS` | `{subjectId?}`（null 為全部） | `EXAMS {exams:[ExamSummary]}`（新到舊） | — |
| `GET_EXAM` | `{examId}` | `EXAM_DETAIL {exam:ExamSummary, questions:[QuestionData]}`（依順序） | `EXAM_NOT_FOUND` |
| `CREATE_EXAM` | `{title, subjectId, year?, examType?}` | `EXAM_SAVED` = ExamSummary | `SUBJECT_NOT_FOUND` |
| `UPDATE_EXAM` | `{examId, title, subjectId, year?, examType?}` | `EXAM_SAVED` | `EXAM_NOT_FOUND`、`NOT_OWNER`、`SUBJECT_NOT_FOUND` |
| `DELETE_EXAM` | `{examId}` | `EXAM_DELETED {examId}` | `EXAM_NOT_FOUND`、`NOT_OWNER` |
| `ADD_QUESTION` | `{examId, question:QuestionData}` | `QUESTION_SAVED {examId, question}`（加在最後） | `EXAM_NOT_FOUND`、`NOT_OWNER`、`GROUP_NOT_FOUND` |
| `UPDATE_QUESTION` | `{examId, questionId, question}` | `QUESTION_SAVED`（選項整組取代，選項 id 會改變） | 同上 + `QUESTION_NOT_FOUND` |
| `DELETE_QUESTION` | `{examId, questionId}` | `QUESTION_DELETED {examId, questionId}` | 同上 |
| `REORDER_QUESTIONS` | `{examId, questionIds:[…]}` | `EXAM_DETAIL` | 同上；清單必須恰好是考卷目前的全部題目，否則 `INVALID_PAYLOAD` |
| `UPDATE_GROUP`（v0.7） | `{examId, groupId, passage}` | `GROUP_SAVED {examId, groupId, passage}`（同組題目一起改變） | `EXAM_NOT_FOUND`、`NOT_OWNER`、`GROUP_NOT_FOUND` |

欄位格式錯誤一律回 `INVALID_PAYLOAD`。刪除考卷時，只屬於這份考卷的題目會一併刪除。
搜尋功能屬於第八階段，本版只提供依分類篩選。

### 3.6 單人練習、紀錄、錯題本（v0.5，第五階段）

全部需要登入。計分、對錯判斷與計時一律由伺服器負責；**開始練習時不傳送答案**，交卷（或練習模式送出該題）後才回傳。

#### 模式

| `mode` | 題目來源 | 參數 |
|---|---|---|
| `FULL_EXAM` | 一份考卷的全部題目，依考卷順序 | `examId` 必填 |
| `RANDOM` | 隨機抽題：`examId` 優先，否則 `subjectId`，兩者皆空為全部題庫 | `count` 1–100，預設 10；題庫不足時全部使用 |
| `WRONG_REVIEW` | 自己的錯題本，順序隨機 | `subjectId` 選填；`count` 選填，預設全部（上限 100） |

| `revealMode` | 說明 |
|---|---|
| `AFTER_SUBMIT` | 考試模式：作答留在客戶端，`SUBMIT_PRACTICE` 一次送出並批改 |
| `IMMEDIATE` | 練習模式：每題以 `ANSWER_QUESTION` 送出並立即批改，同一題不能重送 |

- 只使用**已設定答案**的題目。
- 每位使用者同時只有一個進行中的練習，開始新的會取代舊的。
- 進行中的練習只存在伺服器記憶體，斷線即清除。

#### 計分

| 題型 | 計分方式 |
|---|---|
| 單選、是非 | 答對 1 分，否則 0 分 |
| 多選（學測規則） | n 個選項中有 k 個與正確答案不一致，得 `max(0, (n − 2k) / n)` 分；未作答 0 分 |

- 只有得 1 分（全對）才算答對；其餘（含部分給分、未作答）都算錯題，交卷時加入錯題本或累計錯誤次數。
- 總分：各題得分平均 × 100，四捨五入到小數 1 位。

#### 訊息

| 請求 | payload | 成功回應 | 可能的錯誤 |
|---|---|---|---|
| `START_PRACTICE` | `{mode, revealMode, examId?, subjectId?, count?}` | `PRACTICE_STARTED` | `NO_QUESTIONS`、`EXAM_NOT_FOUND`、`SUBJECT_NOT_FOUND`、`INVALID_PAYLOAD` |
| `ANSWER_QUESTION` | `{practiceId, index, selectedOptionIds?, trueFalseAnswer?}` | `ANSWER_RESULT` | `PRACTICE_NOT_FOUND`、`WRONG_MODE`、`ALREADY_ANSWERED` |
| `SUBMIT_PRACTICE` | `{practiceId, answers:[{index, selectedOptionIds?, trueFalseAnswer?}]}` | `PRACTICE_RESULT` | `PRACTICE_NOT_FOUND` |
| `LIST_RECORDS` | `{}` | `RECORDS {records:[RecordSummary], stats}` | — |
| `GET_RECORD` | `{recordId}` | `PRACTICE_RESULT` | `RECORD_NOT_FOUND`（不存在或不是自己的） |
| `LIST_WRONG` | `{subjectId?}` | `WRONG_LIST {items:[WrongItem]}` | — |
| `REMOVE_WRONG` | `{questionId}` | `WRONG_REMOVED {questionId}` | `QUESTION_NOT_FOUND` |

- `SUBMIT_PRACTICE` 的 `answers`：考試模式只需送已作答的題目；練習模式送空清單（以已批改的為準）。
- 練習模式中途送出 `SUBMIT_PRACTICE` 就是「結束練習」，未作答的題目以 0 分計。

#### 結構

```json
PRACTICE_STARTED {"practiceId":"…","mode":"FULL_EXAM","revealMode":"AFTER_SUBMIT","title":"期中考","startedAt":"2026-11-01T04:00:00.000Z",
                  "questions":[{"index":0,"questionId":31,"type":"SINGLE_CHOICE","content":"1 + 1 = ?","options":[{"id":90,"content":"1"},{"id":91,"content":"2"}],
                                "groupId":null,"passage":null}]}
ANSWER_RESULT    {"index":0,"correct":true,"score":1.0,"correctOptionIds":[91],"correctTrueFalse":null,"explanation":"基本加法"}
RecordSummary    {"id":5,"mode":"FULL_EXAM","revealMode":"AFTER_SUBMIT","title":"期中考","startedAt":"…","finishedAt":"…",
                  "durationMs":185000,"questionCount":3,"correctCount":1,"score":53.3}
PRACTICE_RESULT  {"record":RecordSummary,"questions":[{"index":0,"question":QuestionData（作答當時的快照，含正確答案與解說）,
                  "answer":{"index":0,"selectedOptionIds":[91],"trueFalseAnswer":null},"score":1.0,"correct":true,"answeredAt":null}]}
stats            {"practiceCount":2,"questionCount":6,"correctCount":3,"accuracy":0.5,"averageScore":61.7}
WrongItem        {"questionId":31,"subject":SubjectInfo,"examTitle":"期中考","question":QuestionData（含正確答案與解說）,
                  "wrongCount":2,"lastWrongAt":"…"}
```

- 時間皆為 ISO-8601 UTC。`durationMs` 為伺服器記錄的開始到交卷時間。
- `answeredAt` 只在練習模式記錄。
- 紀錄中的題目是作答當時的**快照**：題目之後被修改或刪除，紀錄仍保持原樣。
- 題目被刪除時會從錯題本消失。錯題本顯示正確答案，因為使用者交卷後已經看過。
- 題組的題目（v0.7）：`PRACTICE_STARTED` 的題目、快照與 `WrongItem.question` 都帶 `groupId` 與 `passage`。隨機抽題只抽到題組中的部分題目時，照常顯示文章。

### 3.7 整批匯入題目（v0.6，第七階段）

| 請求 | payload | 成功回應 | 可能的錯誤 |
|---|---|---|---|
| `IMPORT_QUESTIONS` | `{examId?, newExam?: {title, subjectId, year?, examType?}, questions: [QuestionData], groups?: [passage], groupIndexes?: [int?]}` | `IMPORT_DONE {exam: ExamSummary, importedCount}` | `INVALID_PAYLOAD`、`TOO_MANY_QUESTIONS`、`EXAM_NOT_FOUND`、`NOT_OWNER`、`SUBJECT_NOT_FOUND` |

- `examId`（加到自己的考卷最後）與 `newExam`（建立新考卷）必須**二選一**。
- 每題的規則與 `ADD_QUESTION` 相同（3.5 節的答案規則），答案可以未設定。
- **全部驗證通過才寫入**，並在同一個交易內完成（含建立新考卷）；任何一題無效就整批拒絕，錯誤訊息以「第 N 題：…」開頭。
- 一次最多 200 題（`TOO_MANY_QUESTIONS`）。
- 題目內容來自客戶端本機的 OCR 校正；原始檔案不會傳送（D-027）。
- 題組（v0.7）：`groups` 是要建立的題組文章；`groupIndexes` 與 `questions` 一一對應，第 i 題屬於 `groups[groupIndexes[i]]`，`null` 表示不屬於題組。
  `questions` 內的 `groupId`、`passage` 會被忽略。每個題組至少要有一題；位置超出範圍、數量不一致或文章不合規則時回 `INVALID_PAYLOAD`（「第 N 個題組：…」）。

## 4. 錯誤碼

| code | 說明 | 連線是否關閉 |
|---|---|---|
| `BAD_JSON` | 該行不是合法 JSON 或缺少必填欄位 | 否 |
| `UNKNOWN_TYPE` | 不認得的 `type`，或客戶端送出伺服器專用類型 | 否 |
| `NOT_HELLO` | 尚未完成 HELLO | 否 |
| `ALREADY_WELCOMED` | 重複 HELLO | 否 |
| `INVALID_PAYLOAD` | payload 欄位不符規格 | 否 |
| `LINE_TOO_LONG` | 單行超過長度上限 | 是 |
| `INTERNAL` | 伺服器內部錯誤（含資料庫錯誤） | 否 |
| `NOT_LOGGED_IN` | 尚未登入 | 否 |
| `ALREADY_LOGGED_IN` | 帳號已在他處登入，或本連線已登入 | 否 |
| `INVALID_CREDENTIALS` | 帳號或密碼錯誤 | 否 |
| `USERNAME_TAKEN` | 註冊的帳號已存在 | 否 |
| `ROOM_NOT_FOUND` | 房間代碼不存在 | 否 |
| `ROOM_FULL` | 房間人數已達上限 | 否 |
| `ALREADY_IN_ROOM` | 已在某個房間內 | 否 |
| `NOT_IN_ROOM` | 不在任何房間 | 否 |
| `SUBJECT_NOT_FOUND` | 分類不存在 | 否 |
| `SUBJECT_EXISTS` | 分類名稱已存在 | 否 |
| `EXAM_NOT_FOUND` | 考卷不存在 | 否 |
| `QUESTION_NOT_FOUND` | 題目不存在或不屬於該考卷 | 否 |
| `GROUP_NOT_FOUND` | 題組不存在，或這份考卷中沒有題目屬於這個題組（v0.7） | 否 |
| `NOT_OWNER` | 不是考卷建立者，不能修改 | 否 |
| `TOO_MANY_QUESTIONS` | 一次匯入超過 200 題 | 否 |
| `NO_QUESTIONS` | 沒有可以練習的題目（需已設定答案） | 否 |
| `PRACTICE_NOT_FOUND` | 練習不存在、已交卷或已被新的練習取代 | 否 |
| `ALREADY_ANSWERED` | 練習模式中重複作答同一題 | 否 |
| `WRONG_MODE` | 考試模式不能逐題送出答案 | 否 |
| `RECORD_NOT_FOUND` | 紀錄不存在或不是自己的 | 否 |

## 5. 典型流程

```text
C → S  HELLO                    S → C  WELCOME
C → S  REGISTER                 S → C  REGISTER_OK
C → S  LOGIN                    S → C  LOGIN_OK {online}       S → 其他  CLIENT_JOINED
C → S  CREATE_ROOM              S → C  ROOM_STATE {roomCode}
（B）  JOIN_ROOM {roomCode}      S → B  ROOM_STATE             S → 房內其他人  ROOM_PLAYER_JOINED
C → S  CHAT                     S → 同房  CHAT {roomCode}
房主   LEAVE_ROOM                S → 房主  LEFT_ROOM            S → 房內其他人  ROOM_CLOSED
C → S  BYE                      S → 其他  CLIENT_LEFT
```

## 6. 心跳與逾時

- 客戶端每 15 秒送一次 `PING`。
- 伺服器 45 秒內未收到該連線的任何訊息，判定 `TIMEOUT` 並關閉連線；離線流程與斷線相同。
- 伺服器目前不主動送 `PING`，但客戶端收到 `PING` 時仍必須回 `PONG`。
