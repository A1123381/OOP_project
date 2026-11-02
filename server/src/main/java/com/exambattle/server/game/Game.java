package com.exambattle.server.game;

import com.exambattle.common.protocol.game.PlayerResult;
import com.exambattle.common.protocol.payload.UserInfo;
import com.exambattle.common.protocol.practice.AnswerData;
import com.exambattle.server.Session;
import com.exambattle.server.dao.BankRecords.QuestionRecord;
import com.exambattle.server.room.Room;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

/** 一場進行中的競賽。非執行緒安全，只能在共用鎖內使用（見 {@link GameManager}）。 */
final class Game {

    enum Phase { STARTING, QUESTION, RESULT, OVER }

    /** 一位參賽者；離開房間後 present 為 false，但保留成績與錯題。 */
    static final class Player {
        final UserInfo user;
        Session session;
        boolean present = true;
        int score;
        int correctCount;
        int streak;
        int lastPoints;
        PlayerResult lastResult;
        final Map<Integer, AnswerData> answers = new HashMap<>();
        /** 已公布結果、但沒有全對的題目（交卷時加入錯題本）。 */
        final List<Long> wrong = new ArrayList<>();

        Player(Session session) {
            this.session = session;
            this.user = session.user();
        }
    }

    final String id;
    final Room room;
    final String title;
    final List<QuestionRecord> questions;
    final int secondsPerQuestion;
    final Map<Long, Player> players = new LinkedHashMap<>();
    Phase phase = Phase.STARTING;
    int index = -1;
    Instant deadline;
    int played;
    ScheduledFuture<?> pending;

    Game(String id, Room room, String title, List<QuestionRecord> questions, int secondsPerQuestion,
         List<Session> members) {
        this.id = id;
        this.room = room;
        this.title = title;
        this.questions = List.copyOf(questions);
        this.secondsPerQuestion = secondsPerQuestion;
        members.forEach(s -> players.put(s.user().userId(), new Player(s)));
    }

    List<Player> present() {
        return players.values().stream().filter(p -> p.present).toList();
    }

    boolean allAnswered() {
        return present().stream().allMatch(p -> p.answers.containsKey(index));
    }

    void cancelPending() {
        if (pending != null) {
            pending.cancel(false);
            pending = null;
        }
    }
}
