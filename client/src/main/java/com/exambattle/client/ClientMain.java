package com.exambattle.client;

import com.exambattle.client.console.ConsoleClient;
import com.exambattle.client.console.UserInput;
import com.exambattle.common.protocol.Protocol;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.Charset;

/**
 * 主控台客戶端進入點。連線後以 /register、/login 指令登入。
 *
 * <pre>java -jar client/target/client-1.0.0.jar [host] [port]</pre>
 */
public final class ClientMain {

    private ClientMain() {
    }

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = Protocol.DEFAULT_PORT;
        if (args.length > 1) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("埠號必須是數字: " + args[1]);
                System.exit(2);
            }
        }

        ConsoleClient client = new ConsoleClient(System.out, () -> System.exit(0));
        System.exit(client.run(host, port, userInput()));
    }

    private static UserInput userInput() {
        Console console = System.console();
        if (console != null && isTerminal(console)) {
            return UserInput.of(console);
        }
        return UserInput.of(new BufferedReader(new InputStreamReader(System.in, stdinCharset())), System.out);
    }

    /**
     * JDK 22 起輸入被導向時 System.console() 也不是 null，需以 isTerminal() 判斷；
     * 以 Java 21 編譯，所以用反射呼叫。JDK 21 上 console 不為 null 即代表終端機。
     */
    private static boolean isTerminal(Console console) {
        try {
            Method isTerminal = Console.class.getMethod("isTerminal");
            return (boolean) isTerminal.invoke(console);
        } catch (NoSuchMethodException e) {
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** 導向輸入的編碼：優先使用 JDK 提供的 stdin.encoding，否則使用系統預設。 */
    private static Charset stdinCharset() {
        String encoding = System.getProperty("stdin.encoding");
        if (encoding != null && Charset.isSupported(encoding)) {
            return Charset.forName(encoding);
        }
        return Charset.defaultCharset();
    }
}
