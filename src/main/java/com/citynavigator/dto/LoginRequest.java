package com.citynavigator.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Signing in.
 */
public record LoginRequest(
        @NotBlank(message = "gerekli") @Size(max = 254, message = "en fazla 254 karakter olabilir") String email,
        @NotNull(message = "gerekli") @Size(max = 128, message = "en fazla 128 karakter olabilir") String password) {

    /** Never shows the password, for example in a log. */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + "]";
    }
}
