package com.citynavigator.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Reporting an incident at a point; the server finds the road and the stretch it covers.
 *
 * @param durationMinutes how long it lasts; empty means until someone resolves it
 */
public record IncidentRequest(
        @NotNull(message = "seçilmeli")
        @Pattern(regexp = "ACCIDENT|ROADWORK|CONGESTION|CLOSURE", message = "bilinmeyen olay türü") String type,
        @Size(max = 300, message = "en fazla 300 karakter olabilir") String description,
        @NotNull(message = "gerekli") @DecimalMin(value = "-90", message = "-90 ile 90 arasında olmalı")
        @DecimalMax(value = "90", message = "-90 ile 90 arasında olmalı") Double lat,
        @NotNull(message = "gerekli") @DecimalMin(value = "-180", message = "-180 ile 180 arasında olmalı")
        @DecimalMax(value = "180", message = "-180 ile 180 arasında olmalı") Double lon,
        @Min(value = 15, message = "en az 15 dakika olmalı")
        @Max(value = 43_200, message = "en fazla 30 gün olabilir") Integer durationMinutes) {
}
