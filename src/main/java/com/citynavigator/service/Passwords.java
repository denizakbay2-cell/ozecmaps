package com.citynavigator.service;

import jakarta.enterprise.context.ApplicationScoped;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing with PBKDF2-HMAC-SHA256, as OWASP recommends when Argon2 is not at hand: a random salt per password
 * and many iterations, so that a stolen database does not give the passwords away. The stored form names the
 * algorithm, the iterations, the salt and the hash ({@code pbkdf2-sha256$600000$salt$hash}), so the number of
 * iterations can grow later without breaking older hashes.
 */
@ApplicationScoped
public class Passwords {

    /** OWASP's recommendation for PBKDF2-HMAC-SHA256 (2023). */
    static final int ITERATIONS = 600_000;
    static final String ALGORITHM = "pbkdf2-sha256";
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    private final SecureRandom random = new SecureRandom();
    private final int iterations;
    private volatile String dummy;

    public Passwords() {
        this(ITERATIONS);
    }

    /** For tests: fewer iterations, so that they run fast. */
    Passwords(int iterations) {
        this.iterations = iterations;
    }

    public String hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        Base64.Encoder base64 = Base64.getEncoder().withoutPadding();
        return ALGORITHM + "$" + iterations + "$" + base64.encodeToString(salt) + "$"
                + base64.encodeToString(derive(password, salt, iterations));
    }

    /** Whether the password is the one the stored hash was made from; false for anything that is not such a hash. */
    public boolean matches(String password, String stored) {
        String[] parts = stored == null ? new String[0] : stored.split("\\$");
        if (parts.length != 4 || !ALGORITHM.equals(parts[0])) {
            return false;
        }
        try {
            int rounds = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            // constant-time comparison: how long it takes says nothing about how much of the hash matched
            return rounds > 0 && MessageDigest.isEqual(expected, derive(password, salt, rounds));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Takes as long as checking a real password; used when no account has the given address, so that the answer's
     * timing does not tell which addresses have an account.
     */
    public void checkAgainstNothing(String password) {
        String known = dummy;
        if (known == null) {
            known = hash("no account has this password");
            dummy = known;
        }
        matches(password, known);
    }

    private static byte[] derive(String password, byte[] salt, int rounds) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, rounds, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 is part of every Java runtime", e);
        } finally {
            spec.clearPassword();
        }
    }
}
