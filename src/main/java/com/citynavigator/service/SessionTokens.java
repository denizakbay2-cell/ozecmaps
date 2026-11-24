package com.citynavigator.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.logging.Logger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signed session tokens: {@code v1.<account>.<expiry>.<signature>}, the signature an HMAC-SHA256 with the server's
 * secret ({@code AUTH_SECRET}). The server keeps no sessions, so a sleeping or restarted server does not sign anyone
 * out; without the secret, tokens last only until the next restart.
 */
@ApplicationScoped
public class SessionTokens {

    static final String SECRET_SETTING = "AUTH_SECRET";
    static final Duration LIFETIME = Duration.ofDays(30);
    private static final String VERSION = "v1";
    private static final int MIN_SECRET_LENGTH = 32;
    private static final Logger LOGGER = Logger.getLogger(SessionTokens.class.getName());

    private Clock clock;
    private byte[] secret;

    protected SessionTokens() {
        // for the CDI proxy
    }

    @Inject
    public SessionTokens(Clock clock) {
        this(clock, secretFromEnvironment());
    }

    SessionTokens(Clock clock, byte[] secret) {
        this.clock = clock;
        this.secret = secret.clone();
    }

    /** A new token for the account. */
    public Session issue(long accountId) {
        Instant expires = clock.instant().plus(LIFETIME);
        String payload = VERSION + "." + accountId + "." + expires.getEpochSecond();
        return new Session(payload + "." + sign(payload), expires);
    }

    /** The account a token was issued to, if the token is genuine and has not expired. */
    public Optional<Long> accountOf(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 4 || !VERSION.equals(parts[0])) {
            return Optional.empty();
        }
        String payload = parts[0] + "." + parts[1] + "." + parts[2];
        if (!MessageDigest.isEqual(sign(payload).getBytes(StandardCharsets.US_ASCII),
                parts[3].getBytes(StandardCharsets.US_ASCII))) {
            return Optional.empty();
        }
        try {
            long account = Long.parseLong(parts[1]);
            Instant expires = Instant.ofEpochSecond(Long.parseLong(parts[2]));
            return expires.isAfter(clock.instant()) ? Optional.of(account) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is part of every Java runtime", e);
        }
    }

    private static byte[] secretFromEnvironment() {
        String setting = System.getenv(SECRET_SETTING);
        if (setting != null && setting.strip().length() >= MIN_SECRET_LENGTH) {
            return setting.strip().getBytes(StandardCharsets.UTF_8);
        }
        LOGGER.warning(() -> SECRET_SETTING + " is not set (or shorter than " + MIN_SECRET_LENGTH
                + " characters): sessions end when the server restarts");
        byte[] random = new byte[MIN_SECRET_LENGTH];
        new SecureRandom().nextBytes(random);
        return random;
    }

    /**
     * A token and when it expires.
     */
    public record Session(String token, Instant expiresAt) {
    }
}
