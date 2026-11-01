# OCR 設定（考卷匯入）

「匯入」分頁可以從 PDF 或圖片擷取考題文字（第六階段）。處理方式如下：

- **有文字層的 PDF**：直接讀取文字，速度快，也最準確。
- **掃描的 PDF、照片、圖片**：用 Tesseract OCR 辨識，需要另外下載**語言資料**。

全部處理都在**使用者自己的電腦**上完成，原始檔案不會上傳到伺服器（見 DECISIONS.md D-026、D-027）。

> **本機狀態（2026-11-01）：** 語言資料已下載到專案根目錄的 `tessdata/`，包含 `chi_tra.traineddata`（13.0MB）與 `eng.traineddata`（15.4MB）。
> 從專案根目錄啟動客戶端就能使用 OCR。

## 1. 語言資料

使用 Tesseract 官方的 `tessdata_best`（準確度較高），語言為**繁體中文＋英文**：

| 檔案 | 下載網址 | 大小 |
|---|---|---|
| `chi_tra.traineddata` | https://github.com/tesseract-ocr/tessdata_best/raw/main/chi_tra.traineddata | 約 13MB |
| `eng.traineddata` | https://github.com/tesseract-ocr/tessdata_best/raw/main/eng.traineddata | 約 15MB |

下載後放在專案根目錄的 `tessdata/` 資料夾。這個資料夾已列在 `.gitignore`，不會進入版本控制。

```powershell
New-Item -ItemType Directory -Force tessdata
Invoke-WebRequest https://github.com/tesseract-ocr/tessdata_best/raw/main/chi_tra.traineddata -OutFile tessdata\chi_tra.traineddata
Invoke-WebRequest https://github.com/tesseract-ocr/tessdata_best/raw/main/eng.traineddata -OutFile tessdata\eng.traineddata
```

Tesseract 程式本身已內含在客戶端（Tess4J），**不需要另外安裝 Tesseract**。

## 2. 語言資料的位置

客戶端依下列順序尋找語言資料：

1. 系統屬性 `exambattle.tessdata`，例如：

   ```bash
   java -Dexambattle.tessdata=D:\ocr\tessdata -jar client/target/client-0.1.0-SNAPSHOT.jar
   ```

2. 環境變數 `EXAMBATTLE_TESSDATA`。
3. 啟動時所在目錄下的 `tessdata` 資料夾（從專案根目錄啟動時就是 `tessdata/`）。

從其他目錄啟動客戶端時，請用前兩種方式指定位置。找不到語言資料時，匯入畫面會顯示錯誤；這時有文字層的 PDF 仍然可以擷取。

## 3. 辨識品質建議

- **解析度**：掃描時使用 200–300 DPI。客戶端會把 PDF 掃描頁轉成 200 DPI 影像再辨識，對一般 10–12 號字效果最好。
- **掃描品質**：文件擺正、清晰、對比明顯。歪斜、陰影、反光、摺痕都會降低準確度。
- **檔案格式**：有文字層的 PDF（例如由 Word 匯出）不需要 OCR，結果最準確。
- **強制 OCR**：PDF 的文字層是亂碼（例如字型編碼有問題）時，勾選「強制 OCR」。
- **限制**：單一檔案 50MB 以內、PDF 最多 100 頁。
- **速度**：每頁約 3–5 秒（依電腦而定）；處理中可以按「取消」，已完成的頁面會保留。

## 4. 已知限制

OCR 只辨識文字，**不會理解題目，也不推測答案**，結果一定要人工檢查（第七階段校正）。常見問題如下：

| 內容 | 狀況 |
|---|---|
| 選項記號 | 括號常被漏掉，或把 (C) 讀成 (0、(O。切題時會盡量容錯，並加上 ⚠ 警示 |
| 數學公式、上下標、分數、根號 | 經常辨識錯誤，例如 f(x) 被讀成 fx) |
| 表格、圖片、圖表 | 不會辨識成結構，只會擷取其中的文字，或完全略過 |
| 直排文字 | 不支援（需另外下載 `chi_tra_vert.traineddata`，目前未使用）。辨識時主控台可能出現 `Failed loading language 'chi_tra_vert'`，可以忽略 |
| 手寫 | 準確度很低 |
| 答案卷的 ○ | 常被讀成 0 或 O；匯入答案卷時請在預覽中確認，必要時改成 ○ 再套用 |
| 簡體中文 | 目前只有繁體中文與英文的語言資料 |
| 題組（閱讀測驗、克漏字） | 需要「第 11 至 15 題為題組」之類的標題才能確定範圍；沒有標題時，超過 200 字的無題號文字會暫時當成下一題的文章並警示。規則見 DECISIONS.md D-035 |
| 圖片選項（地圖、圖表） | 只擷取到零碎文字，會警示「選項可能是圖片或圖表」，請改成文字描述 |

## 5. 常見問題

| 狀況 | 處理方式 |
|---|---|
| 「找不到 OCR 語言資料」 | 依第 1 節下載，或以第 2 節的方式指定位置 |
| 「OCR 辨識失敗」且提到 DLL 或 `UnsatisfiedLinkError` | Tess4J 的原生函式庫需要 Microsoft Visual C++ 2015–2022 執行環境（x64）。請從 Microsoft 官方網站安裝 *Visual C++ Redistributable* 後重試 |
| 辨識結果幾乎都是亂碼 | 檢查掃描解析度與清晰度，或改用有文字層的 PDF |
| 有文字層的 PDF 擷取出亂碼 | 勾選「強制 OCR」重新擷取 |
| 「這份 PDF 有密碼保護」 | 先用 PDF 軟體解除保護再匯入 |
