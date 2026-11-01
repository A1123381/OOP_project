package com.exambattle.client.console;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.PrintStream;

/**
 * 主控台輸入來源。互動式終端機使用 {@link Console}（密碼不回顯）；
 * 輸入被導向檔案或管線時改用一般串流，避免兩個讀取端搶同一份緩衝。
 */
public interface UserInput {

    /** 讀取一行；輸入結束時回傳 null。 */
    String readLine() throws IOException;

    /** 顯示提示並讀取密碼；輸入結束時回傳 null。 */
    String readPassword(String prompt) throws IOException;

    /** 是否為互動式終端機（決定要不要顯示提示列）。 */
    boolean interactive();

    static UserInput of(Console console) {
        return new UserInput() {
            @Override
            public String readLine() {
                return console.readLine();
            }

            @Override
            public String readPassword(String prompt) {
                char[] password = console.readPassword("%s", prompt);
                return password == null ? null : new String(password);
            }

            @Override
            public boolean interactive() {
                return true;
            }
        };
    }

    static UserInput of(BufferedReader reader, PrintStream out) {
        return new UserInput() {
            @Override
            public String readLine() throws IOException {
                return reader.readLine();
            }

            @Override
            public String readPassword(String prompt) throws IOException {
                out.print(prompt);
                out.flush();
                return reader.readLine();
            }

            @Override
            public boolean interactive() {
                return false;
            }
        };
    }
}
