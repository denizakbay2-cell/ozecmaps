package com.citynavigator.dto;

import java.time.Instant;

/**
 * A signed-in account as its owner sees it.
 *
 * @param name how the application greets the person; {@code null} when not given
 */
public record AccountView(String email, String name, Instant createdAt) {
}
