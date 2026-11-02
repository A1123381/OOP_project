package com.exambattle.server;

import com.exambattle.common.protocol.payload.LeaveReason;
import com.exambattle.server.auth.AuthService;
import com.exambattle.server.auth.PasswordHasher;
import com.exambattle.server.bank.ExamService;
import com.exambattle.server.bank.SubjectService;
import com.exambattle.server.dao.Repositories;
import com.exambattle.server.handler.BankHandler;
import com.exambattle.server.handler.AuthHandler;
import com.exambattle.server.handler.ChatHandler;
import com.exambattle.server.handler.GameHandler;
import com.exambattle.server.game.GameManager;
import com.exambattle.server.handler.PracticeHandler;
import com.exambattle.server.handler.RoomHandler;
import com.exambattle.server.practice.PracticeService;
import com.exambattle.server.room.RoomCodeGenerator;
import com.exambattle.server.room.RoomManager;

import java.time.Clock;

/**
 * 伺服器共用的狀態與訊息處理器。registry 與 rooms 共用同一把鎖（見 DECISIONS.md D-008）。
 */
record ServerContext(Object lock, ClientRegistry registry, RoomManager rooms,
                     AuthHandler auth, RoomHandler roomHandler, ChatHandler chat, BankHandler bank,
                     PracticeHandler practice, GameManager games, GameHandler game) {

    static ServerContext create(ServerConfig config, Repositories repos, Clock clock) {
        Object lock = new Object();
        ClientRegistry registry = new ClientRegistry(lock);
        RoomManager rooms = new RoomManager(lock, new RoomCodeGenerator(), config.maxRoomPlayers());
        AuthService authService = new AuthService(repos.users(), new PasswordHasher(config.passwordIterations()));
        GameManager games = new GameManager(rooms, repos, config.gameTiming(), clock);
        return new ServerContext(lock, registry, rooms,
                new AuthHandler(authService, registry),
                new RoomHandler(rooms),
                new ChatHandler(lock, registry, rooms, clock),
                new BankHandler(new SubjectService(repos.subjects()), new ExamService(repos)),
                new PracticeHandler(new PracticeService(repos, clock)),
                games, new GameHandler(games));
    }

    /** 連線結束：清除進行中的練習，先離開房間（通知同房的人），再離線（通知所有已登入者）。 */
    void disconnect(Session session, LeaveReason reason) {
        if (session.user() != null) {
            practice.discard(session.user().userId());
        }
        synchronized (lock) {
            rooms.onDisconnect(session);
            registry.logout(session, reason);
        }
    }
}
