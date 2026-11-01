package com.exambattle.client.gui;

import com.exambattle.client.net.ServerConnection;
import com.exambattle.client.net.ServerErrorException;
import com.exambattle.common.protocol.Message;
import com.exambattle.common.protocol.MessageType;
import com.exambattle.common.protocol.Protocol;
import com.exambattle.common.protocol.ProtocolException;
import com.exambattle.common.protocol.payload.HelloPayload;
import javafx.application.Platform;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/**
 * GUI 與伺服器之間的橋接：所有伺服器訊息都切回 JavaFX 執行緒後交給 {@link ClientState}，
 * 請求的結果也在 JavaFX 執行緒回呼，畫面程式不需處理執行緒。
 */
public final class GuiSession implements ServerConnection.Listener {

    private final ClientState state;
    private final ServerConnection connection;
    private volatile Consumer<String> onDisconnected = reason -> { };

    private GuiSession(String host, int port, ClientState state) throws IOException {
        this.state = state;
        this.connection = ServerConnection.connect(host, port, this);
    }

    /** 連線並完成 HELLO（阻塞，請勿在 JavaFX 執行緒呼叫）。 */
    public static GuiSession open(String host, int port, ClientState state) throws IOException {
        GuiSession session = new GuiSession(host, port, state);
        try {
            session.connection.request(MessageType.HELLO, new HelloPayload(Protocol.VERSION)).join();
        } catch (CompletionException e) {
            session.connection.close();
            throw new IOException("握手失敗：" + errorText(e), e);
        }
        session.connection.startHeartbeat(Protocol.CLIENT_PING_INTERVAL);
        return session;
    }

    public ClientState state() {
        return state;
    }

    /** 連線中斷時在 JavaFX 執行緒呼叫。 */
    public void setOnDisconnected(Consumer<String> handler) {
        this.onDisconnected = handler;
    }

    /**
     * 送出請求；成功時把回應轉成 resultType 交給 onSuccess，失敗時把錯誤文字交給 onError。
     * 兩個回呼都在 JavaFX 執行緒執行。resultType 為 Void 時不轉換 payload。
     */
    public <T> void call(MessageType type, Object payload, Class<T> resultType,
                         Consumer<T> onSuccess, Consumer<String> onError) {
        CompletableFuture<Message> future = connection.request(type, payload);
        future.whenComplete((message, error) -> Platform.runLater(() -> {
            if (error != null) {
                onError.accept(errorText(error));
                return;
            }
            try {
                onSuccess.accept(resultType == Void.class ? null : message.payloadAs(resultType));
            } catch (ProtocolException e) {
                onError.accept("伺服器回應格式錯誤：" + e.getMessage());
            }
        }));
    }

    /** 同 {@link #call}，錯誤以對話框顯示。 */
    public <T> void call(MessageType type, Object payload, Class<T> resultType, Consumer<T> onSuccess) {
        call(type, payload, resultType, onSuccess, Dialogs::error);
    }

    /** 送出 BYE 並關閉連線。 */
    public void close() {
        connection.sendByeAndClose();
    }

    @Override
    public void onMessage(Message message) {
        Platform.runLater(() -> state.apply(message));
    }

    @Override
    public void onDisconnected(String reason) {
        Platform.runLater(() -> onDisconnected.accept(reason));
    }

    /** 錯誤的顯示文字：伺服器錯誤顯示伺服器的說明，其他顯示原因。 */
    static String errorText(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof ServerErrorException server) {
            return server.getMessage();
        }
        if (cause instanceof IOException) {
            return "與伺服器的連線中斷：" + cause.getMessage();
        }
        return cause.toString();
    }
}
