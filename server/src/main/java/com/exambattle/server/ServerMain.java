package com.exambattle.server;

import com.exambattle.common.protocol.Protocol;
import com.exambattle.server.dao.DataAccessException;
import com.exambattle.server.dao.Database;
import com.exambattle.server.dao.DbConfig;
import com.exambattle.server.dao.Repositories;

import java.io.IOException;

/**
 * 伺服器進入點。資料庫設定見 docs/DATABASE.md。
 *
 * <pre>java -jar server/target/server-1.0.0.jar [port]</pre>
 */
public final class ServerMain {

    private ServerMain() {
    }

    public static void main(String[] args) throws IOException {
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tT %4$s %5$s%6$s%n");

        int port = Protocol.DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("埠號必須是數字: " + args[0]);
                System.exit(2);
            }
        }

        Database database;
        try {
            database = new Database(DbConfig.load(System.getenv(), DbConfig.DEFAULT_FILE));
            database.verify();
        } catch (IllegalStateException | DataAccessException e) {
            System.err.println("資料庫設定錯誤：" + e.getMessage());
            System.err.println("設定步驟請見 docs/DATABASE.md。");
            System.exit(1);
            return;
        }

        ChatServer server = new ChatServer(ServerConfig.defaults().withPort(port), Repositories.jdbc(database));
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "shutdown"));
        System.out.println("Exam Battle 伺服器已啟動，埠號 " + server.port() + "，按 Ctrl+C 結束。");
    }
}
