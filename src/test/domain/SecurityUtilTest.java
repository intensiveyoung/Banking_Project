package domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityUtilTest {

    @Test
    @DisplayName("Should generate a random 16-byte salt as hexadecimal")
    void testGenerateSalt() {
        String firstSalt = SecurityUtil.generateSalt();
        String secondSalt = SecurityUtil.generateSalt();

        assertTrue(firstSalt.matches("[0-9a-f]{32}"));
        assertTrue(secondSalt.matches("[0-9a-f]{32}"));
        assertNotEquals(firstSalt, secondSalt);
    }

    @Test
    @DisplayName("Should produce a deterministic salted SHA-256 PIN hash")
    void testHashPin() {
        String salt = "00112233445566778899aabbccddeeff";

        String firstHash = SecurityUtil.hashPin("1234", salt);
        String secondHash = SecurityUtil.hashPin("1234", salt);
        String differentSaltHash = SecurityUtil.hashPin("1234", SecurityUtil.generateSalt());

        assertEquals(firstHash, secondHash);
        assertTrue(firstHash.matches("[0-9a-f]{64}"));
        assertNotEquals(firstHash, differentSaltHash);
    }

    @Test
    @DisplayName("Should normalize security answers before hashing")
    void testHashSecurityAnswerNormalization() {
        String salt = "00112233445566778899aabbccddeeff";

        assertEquals(
                SecurityUtil.hashSecurityAnswer("dune", salt),
                SecurityUtil.hashSecurityAnswer("  DUNE  ", salt)
        );
    }
}
