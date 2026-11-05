package com.exambattle.server;

import com.exambattle.server.dao.Repositories;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Exam Battle 的 Socket 伺服器：接受連線，每條連線交給一個 {@link ClientHandler}（virtual thread）。
 */
public final class ChatServer implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(ChatServer.class.getName());

    private final ServerConfig config;
    private final ServerContext context;
    private final Set<ClientHandler> connections = ConcurrentHashMap.newKeySet();
    private final AtomicLong nextClientNumber = new AtomicLong(1);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private ServerSocket serverSocket;
    private volatile boolean running;

    public ChatServer(ServerConfig config, Repositories repos) {
        this(config, repos, Clock.systemUTC());
    }

    ChatServer(ServerConfig config, Repositories repos, Clock clock) {
        this.config = config;
        this.context = ServerContext.create(config, repos, clock);
    }

    /** 綁定埠號並開始接受連線。 */
    public synchronized void start() throws IOException {
        if (running) {
            throw new IllegalStateException("伺服器已啟動");
        }
        serverSocket = new ServerSocket(config.port());
        running = true;
        context.images().startCleanup();
        Thread.ofPlatform().name("accept-" + port()).start(this::acceptLoop);
        LOG.info(() -> "伺服器啟動，埠號 " + port());
    }

    /** 實際監聽的埠號（設定為 0 時由系統挑選）。 */
    public int port() {
        return serverSocket.getLocalPort();
    }

    /** 已登入的使用者數量。 */
    public int onlineCount() {
        return context.registry().size();
    }

    /** 進行中的房間數量。 */
    public int roomCount() {
        return context.rooms().roomCount();
    }

    private void acceptLoop() {
        while (running) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (running) {
                    LOG.log(Level.WARNING, "接受連線失敗", e);
                }
                continue;
            }
            String clientId = "u-" + nextClientNumber.getAndIncrement();
            ClientHandler handler = new ClientHandler(socket, clientId, context, config.idleTimeout());
            connections.add(handler);
            try {
                executor.execute(() -> {
                    try {
                        handler.run();
                    } finally {
                        connections.remove(handler);
                    }
                });
                LOG.info(() -> "新連線 " + clientId + " " + socket.getRemoteSocketAddress());
            } catch (RejectedExecutionException e) {
                // 伺服器正在關閉
                connections.remove(handler);
                closeQuietly(socket);
            }
        }
    }

    /** 對所有連線送出 BYE（SERVER_SHUTDOWN）後關閉伺服器。 */
    @Override
    public synchronized void close() {
        if (!running) {
            return;
        }
        running = false;
        context.registry().beginShutdown();
        try {
            serverSocket.close();
        } catch (IOException e) {
            LOG.log(Level.WARNING, "關閉 ServerSocket 失敗", e);
        }
        connections.forEach(ClientHandler::shutdown);
        context.games().close();
        context.images().close();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        LOG.info("伺服器已關閉");
    }

    private static void closeQuietly(Socket socket) {
        try {
            socket.close();
        } catch (IOException ignored) {
            // 已無法處理
        }
    }
}
