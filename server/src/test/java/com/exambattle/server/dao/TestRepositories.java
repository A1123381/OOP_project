package com.exambattle.server.dao;

/** 測試用的 Repositories：全部存在記憶體，並含初始大分類。透過 test-jar 分享給 tools。 */
public final class TestRepositories {

    private TestRepositories() {
    }

    public static Repositories inMemory() {
        return inMemory(new InMemoryUserDao());
    }

    /** 使用指定的帳號 DAO（例如需要設定管理員的測試）。 */
    public static Repositories inMemory(UserDao users) {
        InMemoryBankDao bank = new InMemoryBankDao().withSeedSubjects();
        InMemoryPracticeDao practice = new InMemoryPracticeDao(bank);
        return new Repositories(users, bank.subjects(), bank.exams(), bank.questions(),
                practice, practice, bank.imports(), new InMemoryImageDao(bank::referencedImageIds));
    }
}
