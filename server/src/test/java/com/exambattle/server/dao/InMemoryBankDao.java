package com.exambattle.server.dao;

import com.exambattle.server.dao.BankRecords.ExamQuery;
import com.exambattle.server.dao.BankRecords.ExamRecord;
import com.exambattle.server.dao.BankRecords.ExamStats;
import com.exambattle.server.dao.BankRecords.NewExam;
import com.exambattle.server.dao.BankRecords.NewOption;
import com.exambattle.server.dao.BankRecords.NewQuestion;
import com.exambattle.server.dao.BankRecords.OptionRecord;
import com.exambattle.server.dao.BankRecords.QuestionRecord;
import com.exambattle.server.dao.BankRecords.SubjectRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 測試用：以記憶體實作題庫的三個 DAO（共用同一份資料），行為與 Jdbc 實作一致
 * （分類名稱不分大小寫唯一、刪除時清除孤兒題目與空的題組、更新題目時選項整組取代）。
 * 三個 DAO 介面的方法名稱有重疊，所以以內部類別分別實作；所有操作都以外層物件同步。
 */
public final class InMemoryBankDao {

    private final Map<Long, SubjectRecord> subjects = new LinkedHashMap<>();
    private final Map<Long, ExamRecord> exams = new LinkedHashMap<>();
    private final Map<Long, QuestionRecord> questions = new LinkedHashMap<>();
    /** 考卷 id → 題目 id（依順序）。 */
    private final Map<Long, List<Long>> examQuestions = new LinkedHashMap<>();
    /** 題組 id → 文章。題目記錄只存 groupId，讀取時才填入文章（文章修改後同組一起改變）。 */
    private final Map<Long, String> groups = new LinkedHashMap<>();
    private long nextId = 1;

    private final SubjectDao subjectDao = new Subjects();
    private final ExamDao examDao = new Exams();
    private final QuestionDao questionDao = new Questions();

    /** 建立與 db/seed_subjects.sql 相同的初始分類。 */
    public InMemoryBankDao withSeedSubjects() {
        synchronized (this) {
            for (String name : List.of("數學", "英文", "國文", "電腦科學", "自然科學", "社會科學", "證照考試", "其他")) {
                long id = nextId++;
                subjects.put(id, new SubjectRecord(id, name));
            }
        }
        return this;
    }

    public SubjectDao subjects() {
        return subjectDao;
    }

    public ExamDao exams() {
        return examDao;
    }

    public QuestionDao questions() {
        return questionDao;
    }

    /** 整批匯入：在同一把鎖內完成，驗證已在服務層做完，因此不會只寫入一部分。 */
    public ImportDao imports() {
        return (examId, newExam, list, passages, groupIndexes) -> {
            synchronized (InMemoryBankDao.this) {
                long target = newExam != null ? examDao.create(newExam).id() : examId;
                List<Long> groupIds = new ArrayList<>();
                for (String passage : passages) {
                    long id = nextId++;
                    groups.put(id, passage);
                    groupIds.add(id);
                }
                for (int i = 0; i < list.size(); i++) {
                    Integer index = groupIndexes.get(i);
                    questionDao.createInExam(target, list.get(i).withGroup(index == null ? null : groupIds.get(index), null));
                }
                deleteEmptyGroups();
                return target;
            }
        };
    }

    private final class Subjects implements SubjectDao {

        @Override
        public List<SubjectRecord> list() {
            synchronized (InMemoryBankDao.this) {
                return subjects.values().stream().sorted(Comparator.comparing(SubjectRecord::name)).toList();
            }
        }

        @Override
        public Optional<SubjectRecord> findById(long id) {
            synchronized (InMemoryBankDao.this) {
                return Optional.ofNullable(subjects.get(id));
            }
        }

        @Override
        public SubjectRecord create(String name, long createdBy) throws SubjectExistsException {
            synchronized (InMemoryBankDao.this) {
                String key = name.toLowerCase(Locale.ROOT);
                if (subjects.values().stream().anyMatch(s -> s.name().toLowerCase(Locale.ROOT).equals(key))) {
                    throw new SubjectExistsException(name);
                }
                long id = nextId++;
                SubjectRecord subject = new SubjectRecord(id, name);
                subjects.put(id, subject);
                return subject;
            }
        }
    }

    private final class Exams implements ExamDao {

        @Override
        public ExamRecord create(NewExam exam) {
            synchronized (InMemoryBankDao.this) {
                long id = nextId++;
                ExamRecord record = new ExamRecord(id, exam.title(), exam.subjectId(), exam.year(), exam.examType(),
                        exam.ownerId(), exam.visibility(), false);
                exams.put(id, record);
                examQuestions.put(id, new ArrayList<>());
                return record;
            }
        }

        @Override
        public void update(ExamRecord exam) {
            synchronized (InMemoryBankDao.this) {
                exams.put(exam.id(), exam);
            }
        }

        @Override
        public void delete(long examId) {
            synchronized (InMemoryBankDao.this) {
                exams.remove(examId);
                List<Long> ids = examQuestions.remove(examId);
                if (ids != null) {
                    ids.forEach(InMemoryBankDao.this::deleteIfOrphan);
                }
                deleteEmptyGroups();
            }
        }

        @Override
        public Optional<ExamStats> findById(long examId) {
            synchronized (InMemoryBankDao.this) {
                return Optional.ofNullable(exams.get(examId)).map(InMemoryBankDao.this::stats);
            }
        }

        @Override
        public List<ExamStats> list(ExamQuery q) {
            synchronized (InMemoryBankDao.this) {
                String keyword = q.keyword() == null || q.keyword().isBlank() ? null : q.keyword().strip().toLowerCase(Locale.ROOT);
                return exams.values().stream()
                        .filter(e -> q.subjectId() == null || e.subjectId() == q.subjectId())
                        .filter(e -> q.viewerId() == null || (q.mineOnly() ? e.ownerId() == q.viewerId()
                                : e.visibleTo(q.viewerId()) || q.includeTakenDown() && e.takenDown()))
                        .filter(e -> keyword == null || matches(e, keyword))
                        .sorted(Comparator.comparingLong(ExamRecord::id).reversed())
                        .map(InMemoryBankDao.this::stats)
                        .toList();
            }
        }
    }

    private final class Questions implements QuestionDao {

        @Override
        public QuestionRecord createInExam(long examId, NewQuestion question) {
            synchronized (InMemoryBankDao.this) {
                long id = nextId++;
                QuestionRecord record = toRecord(id, question);
                questions.put(id, record);
                examQuestions.get(examId).add(id);
                return withPassage(record);
            }
        }

        @Override
        public QuestionRecord update(long questionId, NewQuestion question) {
            synchronized (InMemoryBankDao.this) {
                QuestionRecord record = toRecord(questionId, question);
                questions.put(questionId, record);
                deleteEmptyGroups();
                return withPassage(record);
            }
        }

        @Override
        public Optional<QuestionRecord> findById(long questionId) {
            synchronized (InMemoryBankDao.this) {
                return Optional.ofNullable(questions.get(questionId)).map(InMemoryBankDao.this::withPassage);
            }
        }

        @Override
        public List<QuestionRecord> findByIds(List<Long> questionIds) {
            synchronized (InMemoryBankDao.this) {
                return questionIds.stream().map(questions::get).filter(q -> q != null)
                        .map(InMemoryBankDao.this::withPassage).toList();
            }
        }

        @Override
        public List<Long> answeredIds(Long examId, Long subjectId, Long viewerId) {
            synchronized (InMemoryBankDao.this) {
                return examQuestions.entrySet().stream()
                        .filter(e -> examId != null ? e.getKey().equals(examId)
                                : subjectId == null || exams.get(e.getKey()).subjectId() == subjectId)
                        .filter(e -> viewerId == null || exams.get(e.getKey()).visibleTo(viewerId))
                        .flatMap(e -> e.getValue().stream())
                        .distinct()
                        .filter(id -> questions.get(id).answerSet())
                        .sorted()
                        .toList();
            }
        }

        @Override
        public List<QuestionRecord> findByExam(long examId) {
            synchronized (InMemoryBankDao.this) {
                return byExam(examId);
            }
        }

        @Override
        public List<Long> questionIdsOf(long examId) {
            synchronized (InMemoryBankDao.this) {
                return List.copyOf(examQuestions.getOrDefault(examId, List.of()));
            }
        }

        @Override
        public boolean isInExam(long examId, long questionId) {
            synchronized (InMemoryBankDao.this) {
                return examQuestions.getOrDefault(examId, List.of()).contains(questionId);
            }
        }

        @Override
        public void removeFromExam(long examId, long questionId) {
            synchronized (InMemoryBankDao.this) {
                List<Long> ids = examQuestions.get(examId);
                if (ids != null) {
                    ids.remove(questionId);
                }
                deleteIfOrphan(questionId);
                deleteEmptyGroups();
            }
        }

        @Override
        public void reorder(long examId, List<Long> questionIds) {
            synchronized (InMemoryBankDao.this) {
                examQuestions.put(examId, new ArrayList<>(questionIds));
            }
        }

        @Override
        public boolean isGroupInExam(long examId, long groupId) {
            synchronized (InMemoryBankDao.this) {
                return byExam(examId).stream().anyMatch(q -> Long.valueOf(groupId).equals(q.groupId()));
            }
        }

        @Override
        public void updatePassage(long groupId, String passage) {
            synchronized (InMemoryBankDao.this) {
                groups.replace(groupId, passage);
            }
        }
    }

    // 給 InMemoryPracticeDao 使用（同一個套件）

    synchronized boolean questionExists(long questionId) {
        return questions.containsKey(questionId);
    }

    /** 題目所屬 id 最小的考卷；與 JdbcPracticeDao 的錯題本查詢一致。 */
    synchronized Optional<ExamRecord> firstExamOf(long questionId) {
        return examQuestions.entrySet().stream()
                .filter(e -> e.getValue().contains(questionId))
                .map(Map.Entry::getKey)
                .sorted()
                .findFirst()
                .map(exams::get);
    }

    synchronized Optional<SubjectRecord> subject(long subjectId) {
        return Optional.ofNullable(subjects.get(subjectId));
    }

    // 以下私有方法只在持有外層鎖時呼叫

    private List<QuestionRecord> byExam(long examId) {
        return examQuestions.getOrDefault(examId, List.of()).stream().map(questions::get).map(this::withPassage)
                .toList();
    }

    private ExamStats stats(ExamRecord exam) {
        List<QuestionRecord> qs = byExam(exam.id());
        int unanswered = (int) qs.stream().filter(q -> !q.answerSet()).count();
        return new ExamStats(exam, qs.size(), unanswered);
    }

    private void deleteIfOrphan(long questionId) {
        boolean used = examQuestions.values().stream().anyMatch(ids -> ids.contains(questionId));
        if (!used) {
            questions.remove(questionId);
        }
    }

    /** 搜尋考卷名稱、類型、年份。記憶體版不比對建立者名稱（建立者搜尋由 JdbcBankDaoTest 驗證）。 */
    private static boolean matches(ExamRecord e, String keyword) {
        return e.title().toLowerCase(Locale.ROOT).contains(keyword)
                || e.examType() != null && e.examType().toLowerCase(Locale.ROOT).contains(keyword)
                || e.year() != null && String.valueOf(e.year()).contains(keyword);
    }

    private void deleteEmptyGroups() {
        groups.keySet().removeIf(id -> questions.values().stream().noneMatch(q -> id.equals(q.groupId())));
    }

    private QuestionRecord withPassage(QuestionRecord q) {
        if (q.groupId() == null) {
            return q;
        }
        return new QuestionRecord(q.id(), q.type(), q.content(), q.explanation(), q.trueFalseAnswer(), q.ownerId(),
                q.options(), q.groupId(), groups.get(q.groupId()));
    }

    private QuestionRecord toRecord(long id, NewQuestion q) {
        List<OptionRecord> options = new ArrayList<>();
        for (NewOption option : q.options()) {
            options.add(new OptionRecord(nextId++, option.content(), option.correct()));
        }
        Long groupId = q.groupId();
        if (groupId == null && q.newPassage() != null) {
            groupId = nextId++;
            groups.put(groupId, q.newPassage());
        }
        return new QuestionRecord(id, q.type(), q.content(), q.explanation(), q.trueFalseAnswer(), q.ownerId(),
                List.copyOf(options), groupId, null);
    }
}
