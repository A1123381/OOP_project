package com.exambattle.tools;

import com.exambattle.common.protocol.Protocol;

import java.time.Duration;

/**
 * 多客戶端連線測試的進入點。伺服器需先啟動。
 *
 * <pre>java -jar tools/target/tools-1.0.0.jar [host] [port] [clients] [rooms] [messagesPerClient]</pre>
 */
public final class LoadTestMain {

    private LoadTestMain() {
    }

    public static void main(String[] args) throws InterruptedException {
        MultiClientLoadTest.Options options;
        try {
            options = new MultiClientLoadTest.Options(
                    args.length > 0 ? args[0] : "localhost",
                    args.length > 1 ? Integer.parseInt(args[1]) : Protocol.DEFAULT_PORT,
                    args.length > 2 ? Integer.parseInt(args[2]) : 20,
                    args.length > 3 ? Integer.parseInt(args[3]) : 4,
                    args.length > 4 ? Integer.parseInt(args[4]) : 10,
                    Duration.ofSeconds(60));
        } catch (IllegalArgumentException e) {
            System.err.println("用法：[host] [port] [clients] [rooms] [messagesPerClient]；" + e.getMessage());
            System.exit(2);
            return;
        }

        System.out.printf("連線到 %s:%d，%d 個客戶端分成 %d 個房間，每人送 %d 則聊天…%n",
                options.host(), options.port(), options.clients(), options.rooms(), options.messagesPerClient());
        MultiClientLoadTest.Result result = new MultiClientLoadTest(options).run();

        System.out.printf("完成：%d/%d 個客戶端收到同房全部聊天，耗時 %d ms%n",
                result.succeeded(), result.clients(), result.elapsed().toMillis());
        System.out.println("每個房間的人看到的順序一致：" + (result.sameOrderInEveryRoom() ? "是" : "否"));
        result.failures().forEach(f -> System.out.println("  失敗 " + f));
        System.out.println(result.passed() ? "結果：通過" : "結果：失敗");
        System.exit(result.passed() ? 0 : 1);
    }
}
