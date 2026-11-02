package com.exambattle.server.bank;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.bank.ExamDetail;
import com.exambattle.common.protocol.bank.ExamRequest;
import com.exambattle.common.protocol.bank.ExamSummary;
import com.exambattle.common.protocol.bank.GroupRequest;
import com.exambattle.common.protocol.bank.GroupSaved;
import com.exambattle.common.protocol.bank.ImportDone;
import com.exambattle.common.protocol.bank.ImportRequest;
import com.exambattle.common.protocol.bank.ListExamsRequest;
import com.exambattle.common.protocol.bank.TakeDownRequest;
import com.exambattle.common.protocol.bank.Visibility;
import com.exambattle.common.protocol.bank.OptionData;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.SubjectInfo;
import com.exambattle.common.protocol.payload.UserInfo;
import com.exambattle.server.dao.BankRecords.ExamQuery;
import com.exambattle.server.dao.BankRecords.ExamRecord;
import com.exambattle.server.dao.BankRecords.ExamStats;
import com.exambattle.server.dao.BankRecords.NewExam;
import com.exambattle.server.dao.BankRecords.NewQuestion;
import com.exambattle.server.dao.BankRecords.QuestionRecord;
import com.exambattle.server.dao.Repositories;
import com.exambattle.server.dao.UserRecord;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 考卷與題目管理。
 * <ul>
 *   <li>公開考卷所有登入者都能瀏覽；私人考卷只有建立者看得到，其他人視同不存在（D-039）</li>
 *   <li>只有建立者能修改或刪除（見 DECISIONS.md D-018）；新建考卷預設私人</li>
 *   <li>管理員可以下架別人的公開考卷，下架後建立者不能再公開，直到管理員解除（D-040）</li>
 *   <li>正確答案只提供給建立者，避免之後的練習與競賽作弊</li>
 * </ul>
 */
public final class ExamService {

    private final Repositories repos;

    public ExamService(Repositories repos) {
        this.repos = repos;
    }

    /** 看得到的考卷：自己的、公開未下架的；管理員另外看得到被下架的。可依分類、關鍵字篩選。 */
    public List<ExamSummary> list(ListExamsRequest request, UserInfo viewer) throws BankException {
        String keyword = request.keyword() == null ? null : request.keyword().strip();
        if (keyword != null && keyword.length() > Protocol.MAX_SEARCH_KEYWORD_CHARS) {
            throw BankException.invalid("搜尋關鍵字不可超過 " + Protocol.MAX_SEARCH_KEYWORD_CHARS + " 字元");
        }
        Map<Long, SubjectInfo> subjects = subjectsById();
        ExamQuery query = new ExamQuery(request.subjectId(), keyword == null || keyword.isEmpty() ? null : keyword,
                viewer.userId(), isAdmin(viewer), Boolean.TRUE.equals(request.mineOnly()));
        return repos.exams().list(query).stream().map(stats -> summary(stats, subjects)).toList();
    }

    /** 考卷與題目；非建立者看不到正確答案，看不到的私人考卷視同不存在。 */
    public ExamDetail detail(long examId, UserInfo viewer) throws BankException {
        ExamStats stats = requireVisibleExam(examId, viewer);
        boolean reveal = stats.exam().ownerId() == viewer.userId();
        List<QuestionData> questions = repos.questions().findByExam(examId).stream()
                .map(q -> toData(q, reveal)).toList();
        return new ExamDetail(summary(stats, subjectsById()), questions);
    }

    public ExamSummary create(ExamRequest request, UserInfo user) throws BankException {
        ExamRequest valid = BankValidator.exam(request);
        requireSubject(valid.subjectId());
        ExamRecord created = repos.exams().create(new NewExam(valid.title(), valid.subjectId(), valid.year(),
                valid.examType(), user.userId(), valid.visibility() == null ? Visibility.PRIVATE : valid.visibility()));
        return summary(new ExamStats(created, 0, 0), subjectsById());
    }

    public ExamSummary update(ExamRequest request, UserInfo user) throws BankException {
        if (request.examId() == null) {
            throw BankException.invalid("缺少 examId");
        }
        ExamStats current = requireOwnedExam(request.examId(), user);
        ExamRequest valid = BankValidator.exam(request);
        requireSubject(valid.subjectId());
        Visibility visibility = valid.visibility() == null ? current.exam().visibility() : valid.visibility();
        if (visibility == Visibility.PUBLIC && current.exam().takenDown()) {
            throw new BankException(ErrorCode.EXAM_TAKEN_DOWN, "這份考卷已被管理員下架，不能公開；請洽管理員");
        }
        ExamRecord updated = new ExamRecord(current.exam().id(), valid.title(), valid.subjectId(), valid.year(),
                valid.examType(), current.exam().ownerId(), visibility, current.exam().takenDown());
        repos.exams().update(updated);
        return summary(requireExam(updated.id()), subjectsById());
    }

    /** 管理員下架（改為私人並禁止建立者再公開）或解除下架（仍為私人，由建立者決定是否再公開）。 */
    public ExamSummary takeDown(TakeDownRequest request, UserInfo user) throws BankException {
        if (!isAdmin(user)) {
            throw new BankException(ErrorCode.NOT_ADMIN, "只有管理員可以下架考卷");
        }
        if (request.examId() == null) {
            throw BankException.invalid("缺少 examId");
        }
        ExamStats current = requireExam(request.examId());
        ExamRecord e = current.exam();
        if (request.takenDown() && !e.takenDown() && e.visibility() != Visibility.PUBLIC) {
            throw BankException.invalid("只能下架公開的考卷");
        }
        Visibility visibility = request.takenDown() ? Visibility.PRIVATE : e.visibility();
        repos.exams().update(new ExamRecord(e.id(), e.title(), e.subjectId(), e.year(), e.examType(), e.ownerId(),
                visibility, request.takenDown()));
        return summary(requireExam(e.id()), subjectsById());
    }

    public void delete(long examId, UserInfo user) throws BankException {
        requireOwnedExam(examId, user);
        repos.exams().delete(examId);
    }

    public QuestionData addQuestion(long examId, QuestionData data, UserInfo user) throws BankException {
        requireOwnedExam(examId, user);
        NewQuestion valid = BankValidator.question(data, user.userId());
        requireGroupInExam(examId, valid.groupId());
        return toData(repos.questions().createInExam(examId, valid), true);
    }

    public QuestionData updateQuestion(long examId, long questionId, QuestionData data, UserInfo user)
            throws BankException {
        requireOwnedExam(examId, user);
        requireQuestionInExam(examId, questionId);
        NewQuestion valid = BankValidator.question(data, user.userId());
        requireGroupInExam(examId, valid.groupId());
        return toData(repos.questions().update(questionId, valid), true);
    }

    /** 修改題組文章；同組的題目一起改變（D-034）。 */
    public GroupSaved updateGroup(GroupRequest request, UserInfo user) throws BankException {
        if (request.examId() == null || request.groupId() == null) {
            throw BankException.invalid("缺少 examId 或 groupId");
        }
        requireOwnedExam(request.examId(), user);
        requireGroupInExam(request.examId(), request.groupId());
        String passage = BankValidator.passage(request.passage());
        repos.questions().updatePassage(request.groupId(), passage);
        return new GroupSaved(request.examId(), request.groupId(), passage);
    }

    public void deleteQuestion(long examId, long questionId, UserInfo user) throws BankException {
        requireOwnedExam(examId, user);
        requireQuestionInExam(examId, questionId);
        repos.questions().removeFromExam(examId, questionId);
    }

    /**
     * 整批匯入（第七階段，D-031）：先驗證全部題目與目標考卷，再於同一個交易寫入；
     * 任何一題無效就整批拒絕，錯誤訊息標明題序。答案可以未設定（D-020）。
     */
    public ImportDone importQuestions(ImportRequest request, UserInfo user) throws BankException {
        if ((request.examId() == null) == (request.newExam() == null)) {
            throw BankException.invalid("請選擇匯入到新考卷或自己的既有考卷（二選一）");
        }
        List<QuestionData> questions = request.questions();
        if (questions == null || questions.isEmpty()) {
            throw BankException.invalid("沒有要匯入的題目");
        }
        if (questions.size() > Protocol.MAX_IMPORT_QUESTIONS) {
            throw new BankException(ErrorCode.TOO_MANY_QUESTIONS,
                    "一次最多匯入 " + Protocol.MAX_IMPORT_QUESTIONS + " 題，請分批匯入");
        }
        List<NewQuestion> valid = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            try {
                QuestionData q = questions.get(i);
                // 匯入時題組由 groups／groupIndexes 指定，題目本身的題組欄位不使用
                valid.add(BankValidator.question(q == null ? null : q.withGroup(null, null), user.userId()));
            } catch (BankException e) {
                throw BankException.invalid("第 " + (i + 1) + " 題：" + e.getMessage());
            }
        }
        List<String> groups = new ArrayList<>();
        for (int g = 0; request.groups() != null && g < request.groups().size(); g++) {
            try {
                groups.add(BankValidator.passage(request.groups().get(g)));
            } catch (BankException e) {
                throw BankException.invalid("第 " + (g + 1) + " 個題組：" + e.getMessage());
            }
        }
        List<Integer> groupIndexes = importGroupIndexes(request.groupIndexes(), questions.size(), groups.size());

        NewExam newExam = null;
        if (request.newExam() != null) {
            ExamRequest exam = BankValidator.exam(request.newExam());
            requireSubject(exam.subjectId());
            newExam = new NewExam(exam.title(), exam.subjectId(), exam.year(), exam.examType(), user.userId(),
                    exam.visibility() == null ? Visibility.PRIVATE : exam.visibility());
        } else {
            requireOwnedExam(request.examId(), user);
        }
        long examId = repos.imports().importQuestions(request.examId(), newExam, valid, groups, groupIndexes);
        return new ImportDone(summary(requireExam(examId), subjectsById()), valid.size());
    }

    /** 每題的題組位置必須在範圍內，且每個題組至少有一題。 */
    private static List<Integer> importGroupIndexes(List<Integer> raw, int questionCount, int groupCount)
            throws BankException {
        if (raw == null) {
            if (groupCount > 0) {
                throw BankException.invalid("有題組但沒有指定題目所屬的題組（groupIndexes）");
            }
            return java.util.Collections.nCopies(questionCount, null);
        }
        if (raw.size() != questionCount) {
            throw BankException.invalid("groupIndexes 的數量必須與題目相同");
        }
        boolean[] used = new boolean[groupCount];
        for (int i = 0; i < raw.size(); i++) {
            Integer index = raw.get(i);
            if (index == null) {
                continue;
            }
            if (index < 0 || index >= groupCount) {
                throw BankException.invalid("第 " + (i + 1) + " 題：找不到指定的題組");
            }
            used[index] = true;
        }
        for (int g = 0; g < groupCount; g++) {
            if (!used[g]) {
                throw BankException.invalid("第 " + (g + 1) + " 個題組沒有任何題目");
            }
        }
        return raw;
    }

    /** questionIds 必須恰好是考卷目前的全部題目（不多、不少、不重複）。 */
    public ExamDetail reorder(long examId, List<Long> questionIds, UserInfo user) throws BankException {
        requireOwnedExam(examId, user);
        List<Long> current = repos.questions().questionIdsOf(examId);
        if (questionIds == null || questionIds.size() != current.size()
                || !new HashSet<>(questionIds).equals(new HashSet<>(current))) {
            throw BankException.invalid("題目清單與考卷目前的題目不一致，請重新整理後再試");
        }
        repos.questions().reorder(examId, questionIds);
        return detail(examId, user);
    }

    private ExamStats requireExam(long examId) throws BankException {
        return repos.exams().findById(examId)
                .orElseThrow(() -> new BankException(ErrorCode.EXAM_NOT_FOUND, "找不到考卷 #" + examId));
    }

    /** 看得到的考卷：建立者、公開未下架，或管理員看被下架的考卷。其他情況視同不存在。 */
    private ExamStats requireVisibleExam(long examId, UserInfo viewer) throws BankException {
        ExamStats stats = requireExam(examId);
        ExamRecord e = stats.exam();
        if (!e.visibleTo(viewer.userId()) && !(e.takenDown() && isAdmin(viewer))) {
            throw new BankException(ErrorCode.EXAM_NOT_FOUND, "找不到考卷 #" + examId);
        }
        return stats;
    }

    private boolean isAdmin(UserInfo user) {
        return repos.users().findById(user.userId()).map(UserRecord::admin).orElse(false);
    }

    private ExamStats requireOwnedExam(long examId, UserInfo user) throws BankException {
        ExamStats stats = requireExam(examId);
        if (!stats.exam().visibleTo(user.userId())) {
            throw new BankException(ErrorCode.EXAM_NOT_FOUND, "找不到考卷 #" + examId); // 不透露私人考卷是否存在
        }
        if (stats.exam().ownerId() != user.userId()) {
            throw new BankException(ErrorCode.NOT_OWNER, "只有考卷建立者可以修改");
        }
        return stats;
    }

    private void requireQuestionInExam(long examId, long questionId) throws BankException {
        if (!repos.questions().isInExam(examId, questionId)) {
            throw new BankException(ErrorCode.QUESTION_NOT_FOUND, "考卷中找不到題目 #" + questionId);
        }
    }

    /** groupId 為 null 時不檢查；否則必須是這份考卷中某些題目所屬的題組。 */
    private void requireGroupInExam(long examId, Long groupId) throws BankException {
        if (groupId != null && !repos.questions().isGroupInExam(examId, groupId)) {
            throw new BankException(ErrorCode.GROUP_NOT_FOUND, "考卷中找不到題組 #" + groupId);
        }
    }

    private void requireSubject(long subjectId) throws BankException {
        if (repos.subjects().findById(subjectId).isEmpty()) {
            throw new BankException(ErrorCode.SUBJECT_NOT_FOUND, "找不到分類 #" + subjectId);
        }
    }

    private Map<Long, SubjectInfo> subjectsById() {
        return repos.subjects().list().stream()
                .map(SubjectService::toInfo)
                .collect(Collectors.toMap(SubjectInfo::id, Function.identity()));
    }

    private ExamSummary summary(ExamStats stats, Map<Long, SubjectInfo> subjects) {
        ExamRecord exam = stats.exam();
        SubjectInfo subject = subjects.getOrDefault(exam.subjectId(), new SubjectInfo(exam.subjectId(), "?"));
        UserInfo owner = repos.users().findById(exam.ownerId())
                .map(u -> new UserInfo(u.id(), u.username(), u.displayName()))
                .orElse(new UserInfo(exam.ownerId(), "?", "?"));
        return new ExamSummary(exam.id(), exam.title(), subject, exam.year(), exam.examType(), owner,
                stats.questionCount(), stats.unansweredCount(), exam.visibility(), exam.takenDown());
    }

    /** reveal 為 false 時移除正確答案，只保留「是否已設定」。 */
    static QuestionData toData(QuestionRecord q, boolean reveal) {
        List<OptionData> options = q.options().stream()
                .map(o -> new OptionData(o.id(), o.content(), reveal ? o.correct() : null))
                .toList();
        return new QuestionData(q.id(), q.type(), q.content(), q.explanation(), options,
                reveal ? q.trueFalseAnswer() : null, q.answerSet(), q.groupId(), q.passage());
    }
}
