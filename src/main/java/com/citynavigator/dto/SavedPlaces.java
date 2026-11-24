package com.citynavigator.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Everything an account keeps, read and written as a whole: home, workplace (either may be {@code null}) and the
 * saved places in their order.
 */
public record SavedPlaces(
        @Valid SavedPlaceView home,
        @Valid SavedPlaceView work,
        @NotNull(message = "gerekli") @Size(max = 20, message = "en fazla 20 yer kaydedilebilir")
        List<@Valid @NotNull(message = "boş olamaz") SavedPlaceView> places) {
}
