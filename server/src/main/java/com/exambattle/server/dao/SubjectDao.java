package com.exambattle.server.dao;

import com.exambattle.server.dao.BankRecords.SubjectRecord;

import java.util.List;
import java.util.Optional;

/** subjects 資料表的存取介面。 */
public interface SubjectDao {

    /** 全部大分類，依名稱排序。 */
    List<SubjectRecord> list();

    Optional<SubjectRecord> findById(long id);

    /**
     * 新增大分類。
     *
     * @param createdBy 建立者；種子資料以外一律有值
     * @throws SubjectExistsException 名稱已存在（不分大小寫）
     */
    SubjectRecord create(String name, long createdBy) throws SubjectExistsException;
}
