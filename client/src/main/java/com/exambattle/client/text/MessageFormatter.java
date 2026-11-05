package com.exambattle.client.text;

import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.ProtocolException;
import com.exambattle.common.protocol.game.GameEndReason;
import com.exambattle.common.protocol.game.GameOver;
import com.exambattle.common.protocol.game.GameStarted;
import com.exambattle.common.protocol.game.Kicked;
import com.exambattle.common.protocol.game.LeaderboardEntry;
import com.exambattle.common.protocol.payload.ByePayload;
import com.exambattle.common.protocol.payload.ChatBroadcast;
import com.exambattle.common.protocol.payload.ClientLeftPayload;
import com.exambattle.common.protocol.payload.ErrorPayload;
import com.exambattle.common.protocol.payload.LeftRoom;
import com.exambattle.common.protocol.payload.LoginOk;
import com.exambattle.common.protocol.payload.RoomClosed;
import com.exambattle.common.protocol.payload.RoomPlayerJoined;
import com.exambattle.common.protocol.payload.RoomPlayerLeft;
import com.exambattle.common.protocol.payload.RoomState;
import com.exambattle.common.protocol.payload.UserInfo;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.stream.Collectors;

/** 將伺服器訊息轉成顯示文字，主控台與 GUI 共用。不做任何 I/O，方便測試。 */
public final class MessageFormatter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final ZoneId zone;

    public MessageFormatter(ZoneId zone) {
        this.zone = zone;
    }

    /**
     * 回傳要顯示的文字；不需顯示的訊息（例如 PONG）回傳 empty。
     *
     * @param me 目前登入的使用者，用來標示「你」；尚未登入時為 null
     */
    public Optional<String> format(Message message, UserInfo me) throws ProtocolException {
        MessageType type = MessageType.fromWire(message.type()).orElse(null);
        if (type == null) {
            return Optional.of("? 不認得的訊息類型: " + message.type());
        }
        String text = switch (type) {
            case WELCOME -> "* 已連線到伺服器。請用 /register <帳號> [顯示名稱] 註冊，或 /login <帳號> 登入。";
            case REGISTER_OK -> {
                UserInfo user = message.payloadAs(UserInfo.class);
                yield "* 註冊成功：" + label(user) + "。請輸入 /login " + user.username() + " 登入。";
            }
            case LOGIN_OK -> loginOk(message.payloadAs(LoginOk.class));
            case CLIENT_JOINED -> "* " + label(message.payloadAs(UserInfo.class)) + " 上線了";
            case CLIENT_LEFT -> clientLeft(message.payloadAs(ClientLeftPayload.class));
            case ROOM_STATE -> roomState(message.payloadAs(RoomState.class), me);
            case ROOM_PLAYER_JOINED -> "* " + label(message.payloadAs(RoomPlayerJoined.class).player()) + " 加入了房間";
            case ROOM_PLAYER_LEFT -> roomPlayerLeft(message.payloadAs(RoomPlayerLeft.class));
            case ROOM_CLOSED -> roomClosed(message.payloadAs(RoomClosed.class));
            case LEFT_ROOM -> "* 你已離開房間 " + message.payloadAs(LeftRoom.class).roomCode() + "，回到大廳";
            case CHAT -> chat(message.payloadAs(ChatBroadcast.class), me);
            case ERROR -> {
                ErrorPayload error = message.payloadAs(ErrorPayload.class);
                yield "! 錯誤 [" + error.code() + "] " + error.message();
            }
            case GAME_STARTED -> {
                GameStarted g = message.payloadAs(GameStarted.class);
                yield "* 競賽開始：" + g.title() + "（" + g.questionCount() + " 題，每題 " + g.secondsPerQuestion() + " 秒）";
            }
            case GAME_OVER -> gameOver(message.payloadAs(GameOver.class));
            case KICKED -> "* 你被房主移出房間 " + message.payloadAs(Kicked.class).roomCode() + "，已回到大廳";
            case BYE -> {
                String reason = message.payloadAs(ByePayload.class).reason();
                yield "* 伺服器結束連線" + (reason == null ? "" : "（" + reason + "）");
            }
            // 請求類型，以及由題庫畫面自行顯示的回應
            case HELLO, PING, PONG, REGISTER, LOGIN, CREATE_ROOM, JOIN_ROOM, LEAVE_ROOM, GET_ROOM,
                 LIST_SUBJECTS, SUBJECTS, CREATE_SUBJECT, SUBJECT_SAVED, LIST_EXAMS, EXAMS, GET_EXAM, EXAM_DETAIL,
                 CREATE_EXAM, UPDATE_EXAM, EXAM_SAVED, DELETE_EXAM, EXAM_DELETED, ADD_QUESTION, UPDATE_QUESTION,
                 QUESTION_SAVED, DELETE_QUESTION, QUESTION_DELETED, REORDER_QUESTIONS, IMPORT_QUESTIONS, IMPORT_DONE,
                 UPDATE_GROUP, GROUP_SAVED, TAKE_DOWN_EXAM, UPLOAD_IMAGE, IMAGE_SAVED, GET_IMAGE, IMAGE, START_GAME, GAME_QUESTION, SUBMIT_ANSWER, ANSWER_ACCEPTED, GAME_PROGRESS,
                 QUESTION_RESULT, END_GAME, KICK_PLAYER,
                 START_PRACTICE, PRACTICE_STARTED, ANSWER_QUESTION, ANSWER_RESULT, SUBMIT_PRACTICE, PRACTICE_RESULT,
                 LIST_RECORDS, RECORDS, GET_RECORD, LIST_WRONG, WRONG_LIST, REMOVE_WRONG, WRONG_REMOVED -> null;
        };
        return Optional.ofNullable(text);
    }

    private static String loginOk(LoginOk ok) {
        String online = ok.online().isEmpty()
                ? "目前沒有其他人在線。"
                : "目前在線：" + ok.online().stream().map(MessageFormatter::label).collect(Collectors.joining("、"));
        return "* 登入成功，歡迎 " + ok.user().displayName() + "！" + online
                + "\n  輸入 /create 建立房間，或 /join <代碼> 加入房間。";
    }

    private static String clientLeft(ClientLeftPayload left) {
        String why = switch (left.reason()) {
            case BYE -> "離線了";
            case DISCONNECTED -> "斷線了";
            case TIMEOUT -> "逾時離線";
            case KICKED -> "被移出";
        };
        return "* " + label(left.user()) + " " + why;
    }

    private static String roomState(RoomState room, UserInfo me) {
        StringBuilder text = new StringBuilder("* 房間 ").append(room.roomCode())
                .append("（").append(room.players().size()).append("/").append(room.maxPlayers()).append(" 人）");
        for (UserInfo player : room.players()) {
            text.append("\n    ").append(label(player));
            if (player.userId() == room.ownerUserId()) {
                text.append("【房主】");
            }
            if (me != null && player.userId() == me.userId()) {
                text.append("（你）");
            }
        }
        return text.toString();
    }

    private static String roomPlayerLeft(RoomPlayerLeft left) {
        String why = switch (left.reason()) {
            case LEFT -> "離開了房間";
            case DISCONNECTED -> "斷線，已離開房間";
            case KICKED -> "被房主移出房間";
        };
        return "* " + label(left.player()) + " " + why;
    }

    private static String gameOver(GameOver over) {
        StringBuilder text = new StringBuilder("* 競賽結束：").append(over.title())
                .append(over.reason() == GameEndReason.ENDED_BY_OWNER ? "（房主提前結束）" : "");
        for (LeaderboardEntry e : over.leaderboard()) {
            text.append("\n    第 ").append(e.rank()).append(" 名 ").append(label(e.player()))
                    .append(" ").append(e.score()).append(" 分");
        }
        return text.toString();
    }

    private static String roomClosed(RoomClosed closed) {
        String why = switch (closed.reason()) {
            case OWNER_LEFT -> "房主離開";
            case OWNER_DISCONNECTED -> "房主斷線";
        };
        return "* 房間 " + closed.roomCode() + " 已解散（" + why + "），你已回到大廳";
    }

    /** 一則聊天的顯示文字（GUI 的聊天列表也使用）。 */
    public String chat(ChatBroadcast chat, UserInfo me) {
        boolean mine = me != null && chat.fromUserId() == me.userId();
        return "[" + localTime(chat.serverTime()) + "] " + chat.fromName() + (mine ? "（你）" : "") + ": " + chat.text();
    }

    private String localTime(String serverTime) {
        try {
            return TIME.format(Instant.parse(serverTime).atZone(zone));
        } catch (DateTimeParseException | NullPointerException e) {
            return "--:--:--";
        }
    }

    private static String label(UserInfo user) {
        return user.displayName() + "(" + user.username() + ")";
    }
}
