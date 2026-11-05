package com.exambattle.server.dao;

/** 伺服器使用的全部資料存取物件。 */
public record Repositories(UserDao users, SubjectDao subjects, ExamDao exams, QuestionDao questions,
                           PracticeDao practices, WrongQuestionDao wrongs, ImportDao imports, ImageDao images) {

    /** 以 MariaDB 實作建立。 */
    public static Repositories jdbc(Database database) {
        JdbcPracticeDao practice = new JdbcPracticeDao(database);
        return new Repositories(new JdbcUserDao(database), new JdbcSubjectDao(database),
                new JdbcExamDao(database), new JdbcQuestionDao(database), practice, practice,
                new JdbcImportDao(database), new JdbcImageDao(database));
    }
}
