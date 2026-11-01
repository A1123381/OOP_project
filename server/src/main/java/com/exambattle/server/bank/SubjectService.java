package com.exambattle.server.bank;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.bank.SubjectInfo;
import com.exambattle.server.dao.BankRecords.SubjectRecord;
import com.exambattle.server.dao.SubjectDao;
import com.exambattle.server.dao.SubjectExistsException;

import java.util.List;

/** 大分類：全站共用，任何登入者都可以新增（見 DECISIONS.md D-019）。 */
public final class SubjectService {

    private final SubjectDao subjects;

    public SubjectService(SubjectDao subjects) {
        this.subjects = subjects;
    }

    public List<SubjectInfo> list() {
        return subjects.list().stream().map(SubjectService::toInfo).toList();
    }

    public SubjectInfo create(String rawName, long userId) throws BankException {
        String name = BankValidator.subjectName(rawName);
        try {
            return toInfo(subjects.create(name, userId));
        } catch (SubjectExistsException e) {
            throw new BankException(ErrorCode.SUBJECT_EXISTS, "分類「" + name + "」已存在");
        }
    }

    static SubjectInfo toInfo(SubjectRecord subject) {
        return new SubjectInfo(subject.id(), subject.name());
    }
}
