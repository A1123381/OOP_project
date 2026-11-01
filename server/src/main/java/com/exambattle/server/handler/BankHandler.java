package com.exambattle.server.handler;

import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.ProtocolException;
import com.exambattle.common.protocol.bank.CreateSubjectRequest;
import com.exambattle.common.protocol.bank.ExamList;
import com.exambattle.common.protocol.bank.ExamRef;
import com.exambattle.common.protocol.bank.ExamRequest;
import com.exambattle.common.protocol.bank.GroupRequest;
import com.exambattle.common.protocol.bank.ImportRequest;
import com.exambattle.common.protocol.bank.ListExamsRequest;
import com.exambattle.common.protocol.bank.QuestionRef;
import com.exambattle.common.protocol.bank.QuestionRequest;
import com.exambattle.common.protocol.bank.QuestionSaved;
import com.exambattle.common.protocol.bank.ReorderRequest;
import com.exambattle.common.protocol.bank.SubjectList;
import com.exambattle.common.protocol.payload.UserInfo;
import com.exambattle.server.Session;
import com.exambattle.server.bank.BankException;
import com.exambattle.server.bank.ExamService;
import com.exambattle.server.bank.SubjectService;

import java.util.EnumSet;
import java.util.Set;

/**
 * 處理題庫訊息。呼叫端須保證已登入。
 * 題庫只碰資料庫、不碰房間狀態，所以不持有共用鎖，在各連線自己的 virtual thread 上執行。
 */
public final class BankHandler {

    /** 由本處理器負責的訊息類型。 */
    public static final Set<MessageType> TYPES = EnumSet.of(
            MessageType.LIST_SUBJECTS, MessageType.CREATE_SUBJECT,
            MessageType.LIST_EXAMS, MessageType.GET_EXAM,
            MessageType.CREATE_EXAM, MessageType.UPDATE_EXAM, MessageType.DELETE_EXAM,
            MessageType.ADD_QUESTION, MessageType.UPDATE_QUESTION, MessageType.DELETE_QUESTION,
            MessageType.REORDER_QUESTIONS, MessageType.IMPORT_QUESTIONS, MessageType.UPDATE_GROUP);

    private final SubjectService subjects;
    private final ExamService exams;

    public BankHandler(SubjectService subjects, ExamService exams) {
        this.subjects = subjects;
        this.exams = exams;
    }

    public void handle(Session session, MessageType type, Message message) throws ProtocolException {
        String requestId = message.requestId();
        UserInfo user = session.user();
        try {
            Message reply = switch (type) {
                case LIST_SUBJECTS -> Message.of(MessageType.SUBJECTS, requestId, new SubjectList(subjects.list()));
                case CREATE_SUBJECT -> Message.of(MessageType.SUBJECT_SAVED, requestId,
                        subjects.create(message.payloadAs(CreateSubjectRequest.class).name(), user.userId()));
                case LIST_EXAMS -> Message.of(MessageType.EXAMS, requestId,
                        new ExamList(exams.list(message.payloadAs(ListExamsRequest.class).subjectId())));
                case GET_EXAM -> Message.of(MessageType.EXAM_DETAIL, requestId,
                        exams.detail(examId(message.payloadAs(ExamRef.class).examId(), requestId), user));
                case CREATE_EXAM -> Message.of(MessageType.EXAM_SAVED, requestId,
                        exams.create(message.payloadAs(ExamRequest.class), user));
                case UPDATE_EXAM -> Message.of(MessageType.EXAM_SAVED, requestId,
                        exams.update(message.payloadAs(ExamRequest.class), user));
                case DELETE_EXAM -> {
                    long examId = examId(message.payloadAs(ExamRef.class).examId(), requestId);
                    exams.delete(examId, user);
                    yield Message.of(MessageType.EXAM_DELETED, requestId, new ExamRef(examId));
                }
                case ADD_QUESTION -> {
                    QuestionRequest request = message.payloadAs(QuestionRequest.class);
                    long examId = examId(request.examId(), requestId);
                    yield Message.of(MessageType.QUESTION_SAVED, requestId,
                            new QuestionSaved(examId, exams.addQuestion(examId, request.question(), user)));
                }
                case UPDATE_QUESTION -> {
                    QuestionRequest request = message.payloadAs(QuestionRequest.class);
                    long examId = examId(request.examId(), requestId);
                    long questionId = questionId(request.questionId(), requestId);
                    yield Message.of(MessageType.QUESTION_SAVED, requestId, new QuestionSaved(examId,
                            exams.updateQuestion(examId, questionId, request.question(), user)));
                }
                case DELETE_QUESTION -> {
                    QuestionRef ref = message.payloadAs(QuestionRef.class);
                    long examId = examId(ref.examId(), requestId);
                    long questionId = questionId(ref.questionId(), requestId);
                    exams.deleteQuestion(examId, questionId, user);
                    yield Message.of(MessageType.QUESTION_DELETED, requestId, new QuestionRef(examId, questionId));
                }
                case REORDER_QUESTIONS -> {
                    ReorderRequest request = message.payloadAs(ReorderRequest.class);
                    long examId = examId(request.examId(), requestId);
                    yield Message.of(MessageType.EXAM_DETAIL, requestId,
                            exams.reorder(examId, request.questionIds(), user));
                }
                case IMPORT_QUESTIONS -> Message.of(MessageType.IMPORT_DONE, requestId,
                        exams.importQuestions(message.payloadAs(ImportRequest.class), user));
                case UPDATE_GROUP -> Message.of(MessageType.GROUP_SAVED, requestId,
                        exams.updateGroup(message.payloadAs(GroupRequest.class), user));
                default -> throw new IllegalArgumentException("不是題庫訊息: " + type);
            };
            session.send(reply);
        } catch (BankException e) {
            throw new ProtocolException(e.code(), e.getMessage(), requestId);
        }
    }

    private static long examId(Long examId, String requestId) throws ProtocolException {
        if (examId == null) {
            throw PayloadValidator.invalid("缺少 examId", requestId);
        }
        return examId;
    }

    private static long questionId(Long questionId, String requestId) throws ProtocolException {
        if (questionId == null) {
            throw PayloadValidator.invalid("缺少 questionId", requestId);
        }
        return questionId;
    }
}
