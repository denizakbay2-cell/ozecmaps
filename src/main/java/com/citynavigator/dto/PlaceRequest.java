package com.citynavigator.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A place added by an administrator.
 */
public record PlaceRequest(
        @NotBlank(message = "boş olamaz") @Size(max = 200, message = "en fazla 200 karakter olabilir") String name,
        @NotNull(message = "seçilmeli")
        @Pattern(regexp = "SETTLEMENT|NEIGHBOURHOOD|TRANSIT|STREET|CULTURE|PARK|EDUCATION|SHOPPING|PUBLIC_SERVICE"
                + "|HEALTH|FOOD|GROCERY|SHOP|MONEY|SERVICE|WORSHIP|HOTEL|FUEL|OTHER",
                message = "bilinmeyen kategori") String category,
        @NotNull(message = "gerekli") @DecimalMin(value = "-90", message = "-90 ile 90 arasında olmalı")
        @DecimalMax(value = "90", message = "-90 ile 90 arasında olmalı") Double lat,
        @NotNull(message = "gerekli") @DecimalMin(value = "-180", message = "-180 ile 180 arasında olmalı")
        @DecimalMax(value = "180", message = "-180 ile 180 arasında olmalı") Double lon) {
}
