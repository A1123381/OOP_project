package com.exambattle.server.handler;

import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.ProtocolException;
import com.exambattle.common.protocol.game.StartGameRequest;
import com.exambattle.common.protocol.game.SubmitAnswerRequest;
import com.exambattle.server.Session;
import com.exambattle.server.game.GameManager;

/** 處理 START_GAME、SUBMIT_ANSWER、END_GAME（第四階段）。呼叫端須保證已登入。 */
public final class GameHandler {

    private final GameManager games;

    public GameHandler(GameManager games) {
        this.games = games;
    }

    public void handle(Session session, MessageType type, Message message) throws ProtocolException {
        String requestId = message.requestId();
        switch (type) {
            case START_GAME -> games.start(session, message.payloadAs(StartGameRequest.class), requestId);
            case SUBMIT_ANSWER -> games.submit(session, message.payloadAs(SubmitAnswerRequest.class), requestId);
            case END_GAME -> games.end(session, requestId);
            default -> throw new IllegalArgumentException("不是競賽訊息: " + type);
        }
    }
}
