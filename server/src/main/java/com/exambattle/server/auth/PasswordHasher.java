package com.exambattle.server.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * 以 JDK 內建的 PBKDF2WithHmacSHA256 雜湊密碼（見 DECISIONS.md D-011）。
 * 不保存明文；比對時使用固定時間比較。
 */
public final class PasswordHasher {

    /** OWASP 對 PBKDF2-HMAC-SHA256 建議的迭代次數。 */
    public static final int DEFAULT_ITERATIONS = 210_000;

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    /** 雜湊結果。 */
    public record Hashed(byte[] hash, byte[] salt, int iterations) {
    }

    private final int iterations;
    private final SecureRandom random = new SecureRandom();

    public PasswordHasher(int iterations) {
        if (iterations < 1) {
            throw new IllegalArgumentException("iterations 必須大於 0");
        }
        this.iterations = iterations;
    }

    public Hashed hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return new Hashed(derive(password, salt, iterations), salt, iterations);
    }

    /** 以儲存的 salt 與迭代次數重新計算並比對。 */
    public boolean verify(String password, byte[] expectedHash, byte[] salt, int storedIterations) {
        return MessageDigest.isEqual(derive(password, salt, storedIterations), expectedHash);
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JDK 不支援 " + ALGORITHM, e);
        } finally {
            spec.clearPassword();
        }
    }
}
