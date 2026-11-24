package com.citynavigator.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Opening an account.
 *
 * @param name how the application greets the person (optional)
 */
public record RegisterRequest(
        @NotBlank(message = "gerekli") @Email(message = "geçerli bir e-posta adresi olmalı")
        @Size(max = 254, message = "en fazla 254 karakter olabilir") String email,
        @NotNull(message = "gerekli") @Size(min = 8, max = 128, message = "8 ile 128 karakter arasında olmalı")
        String password,
        @Size(max = 60, message = "en fazla 60 karakter olabilir") String name) {

    /** Never shows the password, for example in a log. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", name=" + name + "]";
    }
}
