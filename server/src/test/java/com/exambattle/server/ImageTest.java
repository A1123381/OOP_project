package com.exambattle.server;

import com.exambattle.common.protocol.ErrorCode;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.bank.ExamDetail;
import com.exambattle.common.protocol.bank.ExamRef;
import com.exambattle.common.protocol.bank.ExamRequest;
import com.exambattle.common.protocol.bank.ExamSummary;
import com.exambattle.common.protocol.bank.GroupRequest;
import com.exambattle.common.protocol.bank.GroupSaved;
import com.exambattle.common.protocol.bank.ImageData;
import com.exambattle.common.protocol.bank.ImageRef;
import com.exambattle.common.protocol.bank.ImageSaved;
import com.exambattle.common.protocol.bank.ImageUpload;
import com.exambattle.common.protocol.bank.ImportDone;
import com.exambattle.common.protocol.bank.ImportRequest;
import com.exambattle.common.protocol.bank.OptionData;
import com.exambattle.common.protocol.bank.QuestionData;
import com.exambattle.common.protocol.bank.QuestionRequest;
import com.exambattle.common.protocol.bank.QuestionSaved;
import com.exambattle.common.protocol.bank.QuestionType;
import com.exambattle.common.protocol.bank.SubjectList;
import com.exambattle.common.protocol.bank.Visibility;
import com.exambattle.common.protocol.practice.PracticeMode;
import com.exambattle.common.protocol.practice.PracticeQuestion;
import com.exambattle.common.protocol.practice.PracticeStarted;
import com.exambattle.common.protocol.practice.RevealMode;
import com.exambattle.common.protocol.practice.StartPracticeRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 題目圖片（D-043、D-044），經由協定測試。 */
class ImageTest extends ServerTestBase {

    private TestClient owner;
    private TestClient student;
    private long examId;
    private long math;

    @BeforeEach
    void setUp() throws Exception {
        owner = loggedIn("teacher");
        student = loggedIn("student");
        math = owner.request(MessageType.LIST_SUBJECTS, null, MessageType.SUBJECTS).payloadAs(SubjectList.class)
                .subjects().stream().filter(s -> s.name().equals("數學")).findFirst().orElseThrow().id();
        examId = owner.request(MessageType.CREATE_EXAM, new ExamRequest(null, "圖片卷", math, null, null, Visibility.PUBLIC),
                MessageType.EXAM_SAVED).payloadAs(ExamSummary.class).id();
    }

    @Test
    void uploadValidatesAndDeduplicates() throws Exception {
        byte[] png = image("png", 40, 30, Color.RED);
        ImageSaved saved = upload(owner, "image/png", png);
        assertEquals(32, saved.imageId().length());
        assertEquals(40, saved.width());
        assertEquals(30, saved.height());
        assertEquals(saved.imageId(), upload(student, "image/png", png).imageId(), "相同內容只存一份");
        assertNotEquals(saved.imageId(), upload(owner, "image/jpeg", image("jpg", 40, 30, Color.BLUE)).imageId());

        ImageData data = student.request(MessageType.GET_IMAGE, new ImageRef(saved.imageId()), MessageType.IMAGE)
                .payloadAs(ImageData.class);
        assertEquals("image/png", data.mime());
        assertTrue(Arrays.equals(png, Base64.getDecoder().decode(data.data())));

        owner.expectError(MessageType.UPLOAD_IMAGE, new ImageUpload("image/jpeg", b64(png)), ErrorCode.IMAGE_INVALID);
        owner.expectError(MessageType.UPLOAD_IMAGE, new ImageUpload("image/gif", b64(png)), ErrorCode.IMAGE_INVALID);
        owner.expectError(MessageType.UPLOAD_IMAGE, new ImageUpload("image/png", "不是 base64"), ErrorCode.IMAGE_INVALID);
        owner.expectError(MessageType.UPLOAD_IMAGE, new ImageUpload("image/png", b64(new byte[1_048_577])),
                ErrorCode.IMAGE_INVALID);
        owner.expectError(MessageType.UPLOAD_IMAGE, new ImageUpload("image/png", b64(image("png", 4001, 1, Color.RED))),
                ErrorCode.IMAGE_INVALID);
        owner.expectError(MessageType.GET_IMAGE, new ImageRef("0".repeat(32)), ErrorCode.IMAGE_NOT_FOUND);
    }

    @Test
    void questionsOptionsAndGroupsCarryImages() throws Exception {
        String stem = upload(owner, "image/png", image("png", 20, 20, Color.RED)).imageId();
        String mapA = upload(owner, "image/png", image("png", 20, 20, Color.GREEN)).imageId();
        String mapB = upload(owner, "image/png", image("png", 20, 20, Color.BLUE)).imageId();
        String passage = upload(owner, "image/png", image("png", 20, 20, Color.ORANGE)).imageId();

        QuestionData q = new QuestionData(null, QuestionType.SINGLE_CHOICE, "", null,
                List.of(new OptionData(null, "", true, mapA), new OptionData(null, "", false, mapB)),
                null, false, null, "文章", stem, passage);
        QuestionData saved = owner.request(MessageType.ADD_QUESTION, new QuestionRequest(examId, null, q),
                MessageType.QUESTION_SAVED).payloadAs(QuestionSaved.class).question();
        assertEquals(stem, saved.imageId());
        assertEquals(passage, saved.passageImageId());
        assertEquals(Arrays.asList(mapA, mapB), saved.options().stream().map(OptionData::imageId).toList());

        ExamDetail seen = student.request(MessageType.GET_EXAM, new ExamRef(examId), MessageType.EXAM_DETAIL)
                .payloadAs(ExamDetail.class);
        assertEquals(mapA, seen.questions().get(0).options().get(0).imageId(), "非建立者也看得到圖片（不是答案）");
        assertNull(seen.questions().get(0).options().get(0).correct());

        GroupSaved group = owner.request(MessageType.UPDATE_GROUP, new GroupRequest(examId, saved.groupId(), "新文章", null),
                MessageType.GROUP_SAVED).payloadAs(GroupSaved.class);
        assertNull(group.imageId(), "UPDATE_GROUP 整個取代：null 表示移除圖片");

        PracticeStarted p = student.request(MessageType.START_PRACTICE,
                new StartPracticeRequest(PracticeMode.FULL_EXAM, RevealMode.AFTER_SUBMIT, examId, null, null),
                MessageType.PRACTICE_STARTED).payloadAs(PracticeStarted.class);
        PracticeQuestion pq = p.questions().get(0);
        assertEquals(stem, pq.imageId());
        assertEquals(mapA, pq.options().get(0).imageId());
    }

    @Test
    void missingOrBadImagesAreRejected() throws Exception {
        QuestionData noText = new QuestionData(null, QuestionType.SINGLE_CHOICE, "題目", null,
                List.of(new OptionData(null, "", false), new OptionData(null, "乙", true)), null, false);
        owner.expectError(MessageType.ADD_QUESTION, new QuestionRequest(examId, null, noText), ErrorCode.INVALID_PAYLOAD);

        QuestionData ghost = new QuestionData(null, QuestionType.TRUE_FALSE, "", null, List.of(), true, false,
                null, null, "a".repeat(32), null);
        owner.expectError(MessageType.ADD_QUESTION, new QuestionRequest(examId, null, ghost), ErrorCode.IMAGE_NOT_FOUND);
        QuestionData bad = new QuestionData(null, QuestionType.TRUE_FALSE, "題目", null, List.of(), true, false,
                null, null, "../etc/passwd", null);
        owner.expectError(MessageType.ADD_QUESTION, new QuestionRequest(examId, null, bad), ErrorCode.IMAGE_NOT_FOUND);
    }

    @Test
    void importCarriesQuestionAndGroupImages() throws Exception {
        String opt = upload(owner, "image/png", image("png", 10, 10, Color.RED)).imageId();
        String passage = upload(owner, "image/png", image("png", 10, 10, Color.BLUE)).imageId();
        QuestionData q = new QuestionData(null, QuestionType.SINGLE_CHOICE, "看圖作答", null,
                List.of(new OptionData(null, "", true, opt), new OptionData(null, "乙", false)), null, false);

        ImportDone done = owner.request(MessageType.IMPORT_QUESTIONS, new ImportRequest(null,
                new ExamRequest(null, "匯入圖片", math, null, null), List.of(q), List.of("文章"), List.of(0), List.of(passage)),
                MessageType.IMPORT_DONE).payloadAs(ImportDone.class);
        QuestionData loaded = owner.request(MessageType.GET_EXAM, new ExamRef(done.exam().id()), MessageType.EXAM_DETAIL)
                .payloadAs(ExamDetail.class).questions().get(0);
        assertEquals(opt, loaded.options().get(0).imageId());
        assertEquals(passage, loaded.passageImageId());

        owner.expectError(MessageType.IMPORT_QUESTIONS, new ImportRequest(null, new ExamRequest(null, "x", math, null, null),
                List.of(q), List.of("文章"), List.of(0), List.of("b".repeat(32))), ErrorCode.IMAGE_NOT_FOUND);
        owner.expectError(MessageType.IMPORT_QUESTIONS, new ImportRequest(null, new ExamRequest(null, "x", math, null, null),
                List.of(q), List.of("文章"), List.of(0), List.of()), ErrorCode.INVALID_PAYLOAD);
    }

    // ---------- 輔助

    private static ImageSaved upload(TestClient c, String mime, byte[] bytes) throws Exception {
        return c.request(MessageType.UPLOAD_IMAGE, new ImageUpload(mime, b64(bytes)), MessageType.IMAGE_SAVED)
                .payloadAs(ImageSaved.class);
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    static byte[] image(String format, int width, int height, Color color) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }
}
