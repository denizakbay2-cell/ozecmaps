package com.citynavigator.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A place someone keeps: its name ("Moda Sahili"), where it is ("Caferağa, Kadıköy"), its coordinates.
 */
public record SavedPlaceView(
        @NotBlank(message = "boş olamaz") @Size(max = 200, message = "en fazla 200 karakter olabilir") String label,
        @Size(max = 200, message = "en fazla 200 karakter olabilir") String detail,
        @NotNull(message = "gerekli") @DecimalMin(value = "-90", message = "-90 ile 90 arasında olmalı")
        @DecimalMax(value = "90", message = "-90 ile 90 arasında olmalı") Double lat,
        @NotNull(message = "gerekli") @DecimalMin(value = "-180", message = "-180 ile 180 arasında olmalı")
        @DecimalMax(value = "180", message = "-180 ile 180 arasında olmalı") Double lon) {
}
