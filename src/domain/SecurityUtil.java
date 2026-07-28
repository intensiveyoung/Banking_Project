package domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public final class SecurityUtil {
    private static final int SALT_LENGTH_BYTES = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private SecurityUtil() {}

    public static String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(salt);
        return toHex(salt);
    }

    public static String hashPin(String pin, String salt) {
        if (pin == null || salt == null) {
            throw new IllegalArgumentException("PIN and salt are required.");
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((salt + pin).getBytes(StandardCharsets.UTF_8));
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 hashing is not available.", e);
        }
    }

    public static String hashSecurityAnswer(String answer, String salt) {
        if (answer == null || answer.trim().isEmpty()) {
            throw new IllegalArgumentException("Security answer cannot be empty.");
        }
        return hashPin(answer.trim().toLowerCase(java.util.Locale.ROOT), salt);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}
