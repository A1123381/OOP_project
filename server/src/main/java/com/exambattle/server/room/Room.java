package com.exambattle.server.room;

import com.exambattle.common.protocol.payload.RoomState;
import com.exambattle.common.protocol.payload.UserInfo;
import com.exambattle.server.Session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 一個競賽房間。非執行緒安全，只能在 RoomManager 的鎖內使用。 */
public final class Room {

    private final String code;
    private final long ownerUserId;
    private final int maxPlayers;
    private final Map<Long, Session> players = new LinkedHashMap<>();
    private boolean playing;

    Room(String code, Session owner, int maxPlayers) {
        this.code = code;
        this.ownerUserId = owner.user().userId();
        this.maxPlayers = maxPlayers;
        players.put(ownerUserId, owner);
    }

    public String code() {
        return code;
    }

    public long ownerUserId() {
        return ownerUserId;
    }

    public boolean isOwner(Session session) {
        return session.user().userId() == ownerUserId;
    }

    boolean isFull() {
        return players.size() >= maxPlayers;
    }

    /** 競賽進行中（第四階段）：不能加入或移出玩家。 */
    public boolean playing() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    /** 這位使用者目前的連線；不在房間時為 null。 */
    Session member(long userId) {
        return players.get(userId);
    }

    void add(Session session) {
        players.put(session.user().userId(), session);
    }

    void remove(Session session) {
        players.remove(session.user().userId());
    }

    /** 目前的玩家，依加入順序排列。 */
    public List<Session> members() {
        return new ArrayList<>(players.values());
    }

    public RoomState state() {
        List<UserInfo> infos = players.values().stream().map(Session::user).toList();
        return new RoomState(code, ownerUserId, maxPlayers, infos, playing);
    }
}
