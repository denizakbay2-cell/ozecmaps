package com.citynavigator.dto;

import java.time.Instant;

/**
 * A new session: the token to send as {@code Authorization: Bearer <token>}, and the account it signs in.
 */
public record SessionView(String token, Instant expiresAt, AccountView account) {
}
