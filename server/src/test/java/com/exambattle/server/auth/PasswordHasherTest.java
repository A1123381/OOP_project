package com.exambattle.server.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(1_000);

    @Test
    void verifiesCorrectPasswordOnly() {
        PasswordHasher.Hashed hashed = hasher.hash("correct horse");

        assertTrue(hasher.verify("correct horse", hashed.hash(), hashed.salt(), hashed.iterations()));
        assertFalse(hasher.verify("correct horsE", hashed.hash(), hashed.salt(), hashed.iterations()));
    }

    @Test
    void sizesMatchSchemaColumns() {
        PasswordHasher.Hashed hashed = hasher.hash("考試密碼12345");

        assertEquals(32, hashed.hash().length, "password_hash 為 VARBINARY(32)");
        assertEquals(16, hashed.salt().length, "password_salt 為 VARBINARY(16)");
        assertEquals(1_000, hashed.iterations());
    }

    @Test
    void samePasswordGetsDifferentSalt() {
        PasswordHasher.Hashed a = hasher.hash("same password");
        PasswordHasher.Hashed b = hasher.hash("same password");

        assertFalse(java.util.Arrays.equals(a.salt(), b.salt()));
        assertFalse(java.util.Arrays.equals(a.hash(), b.hash()));
    }

    @Test
    void verifyUsesStoredIterations() {
        PasswordHasher.Hashed old = new PasswordHasher(500).hash("pw-12345678");

        assertTrue(hasher.verify("pw-12345678", old.hash(), old.salt(), 500));
    }

    @Test
    void rejectsNonPositiveIterations() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(0));
    }
}
