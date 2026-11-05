package com.exambattle.server.bank;

import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.bank.ExamRequest;
import com.exambattle.common.protocol.bank.OptionData;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.server.dao.BankRecords.NewOption;
import com.exambattle.server.dao.BankRecords.NewQuestion;

import java.util.ArrayList;
import java.util.List;

/**
 * 題庫欄位與答案規則（見 DECISIONS.md D-020）：
 * <ul>
 *   <li>選擇題 2–10 個選項，內容不可空白；單選題最多 1 個正確選項，多選題不限</li>
 *   <li>沒有正確選項或是非題答案為 null，表示「答案未設定」；系統不推測答案</li>
 *   <li>題組文章 1–20,000 字元（D-034）</li>
 *   <li>題目與選項可以附圖片；有圖片時文字可以空白（D-043）</li>
 * </ul>
 */
final class BankValidator {

    private BankValidator() {
    }

    static String subjectName(String raw) throws BankException {
        return requiredText(raw, Protocol.MAX_SUBJECT_NAME_CHARS, "分類名稱");
    }

    /** 驗證考卷欄位並回傳整理後的值（去除前後空白、空字串改為 null）。 */
    static ExamRequest exam(ExamRequest request) throws BankException {
        String title = requiredText(request.title(), Protocol.MAX_EXAM_TITLE_CHARS, "考卷名稱");
        if (request.subjectId() == null) {
            throw BankException.invalid("請選擇分類");
        }
        Integer year = request.year();
        if (year != null && (year < Protocol.MIN_EXAM_YEAR || year > Protocol.MAX_EXAM_YEAR)) {
            throw BankException.invalid("考試年份必須介於 " + Protocol.MIN_EXAM_YEAR + "–" + Protocol.MAX_EXAM_YEAR);
        }
        String examType = optionalText(request.examType(), Protocol.MAX_EXAM_TYPE_CHARS, "考試類型");
        return new ExamRequest(request.examId(), title, request.subjectId(), year, examType, request.visibility());
    }

    /** 驗證題目並轉成要寫入資料庫的內容。 */
    static NewQuestion question(QuestionData data, long ownerId) throws BankException {
        if (data == null) {
            throw BankException.invalid("缺少 question");
        }
        if (data.type() == null) {
            throw BankException.invalid("請選擇題型");
        }
        String imageId = imageId(data.imageId());
        // 題目內容：文字或題幹圖片至少要有一個（D-043）
        String content = imageId != null && (data.content() == null || data.content().isBlank())
                ? "" : requiredText(data.content(), Protocol.MAX_QUESTION_CHARS, "題目內容");
        String passageImageId = data.groupId() == null && data.passage() != null ? imageId(data.passageImageId()) : null;
        String explanation = optionalText(data.explanation(), Protocol.MAX_EXPLANATION_CHARS, "解說");
        List<OptionData> rawOptions = data.options() == null ? List.of() : data.options();

        if (data.type() == QuestionType.TRUE_FALSE) {
            if (!rawOptions.isEmpty()) {
                throw BankException.invalid("是非題不使用選項");
            }
            return new NewQuestion(data.type(), content, explanation, data.trueFalseAnswer(), ownerId, List.of(),
                    data.groupId(), newPassage(data), imageId, passageImageId);
        }

        if (rawOptions.size() < Protocol.MIN_OPTIONS || rawOptions.size() > Protocol.MAX_OPTIONS) {
            throw BankException.invalid("選擇題需要 " + Protocol.MIN_OPTIONS + "–" + Protocol.MAX_OPTIONS + " 個選項");
        }
        List<NewOption> options = new ArrayList<>();
        for (int i = 0; i < rawOptions.size(); i++) {
            OptionData option = rawOptions.get(i);
            if (option == null) {
                throw BankException.invalid("第 " + (i + 1) + " 個選項是空的");
            }
            String optionImage = imageId(option.imageId());
            // 選項：文字或圖片至少要有一個（例如選項只有一張地圖）
            String text = optionImage != null && (option.content() == null || option.content().isBlank())
                    ? "" : requiredText(option.content(), Protocol.MAX_OPTION_CHARS, "第 " + (i + 1) + " 個選項");
            options.add(new NewOption(text, Boolean.TRUE.equals(option.correct()), optionImage));
        }
        long correct = options.stream().filter(NewOption::correct).count();
        if (data.type() == QuestionType.SINGLE_CHOICE && correct > 1) {
            throw BankException.invalid("單選題只能有一個正確答案");
        }
        return new NewQuestion(data.type(), content, explanation, null, ownerId, options,
                data.groupId(), newPassage(data), imageId, passageImageId);
    }

    /** 圖片 id：null，或 32 個十六進位小寫字元（D-044）。是否存在由 ExamService 檢查。 */
    static String imageId(String raw) throws BankException {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (!raw.matches("[0-9a-f]{32}")) {
            throw new BankException(com.exambattle.common.protocol.ErrorCode.IMAGE_NOT_FOUND, "圖片 id 格式錯誤");
        }
        return raw;
    }

    /** 題組文章（D-034）：1–{@value Protocol#MAX_PASSAGE_CHARS} 字元。 */
    static String passage(String raw) throws BankException {
        return requiredText(raw, Protocol.MAX_PASSAGE_CHARS, "題組文章");
    }

    /** 只帶 passage（沒有 groupId）表示建立新題組；帶 groupId 時忽略 passage。 */
    private static String newPassage(QuestionData data) throws BankException {
        return data.groupId() == null && data.passage() != null ? passage(data.passage()) : null;
    }

    private static String requiredText(String raw, int max, String field) throws BankException {
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty()) {
            throw BankException.invalid(field + "不可空白");
        }
        if (text.length() > max) {
            throw BankException.invalid(field + "不可超過 " + max + " 字元");
        }
        return text;
    }

    private static String optionalText(String raw, int max, String field) throws BankException {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return requiredText(raw, max, field);
    }
}
