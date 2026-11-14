package com.citynavigator.resource;

import com.citynavigator.routing.GeoPoint;
import jakarta.ws.rs.BadRequestException;

/**
 * Query parameters of the form {@code lat,lon}, the order used by the map client and most geo tools.
 */
final class Coordinates {

    static final String PATTERN = "\\s*-?\\d{1,3}(\\.\\d{1,10})?\\s*,\\s*-?\\d{1,3}(\\.\\d{1,10})?\\s*";
    static final String MESSAGE = "enlem,boylam biçiminde olmalı, örneğin 40.9903,29.0230";

    private Coordinates() {
    }

    /**
     * Parses a value that already matched {@link #PATTERN}; values out of range are a bad request.
     */
    static GeoPoint parse(String value, String parameter) {
        String[] parts = value.split(",");
        try {
            return new GeoPoint(Double.parseDouble(parts[0].strip()), Double.parseDouble(parts[1].strip()));
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            throw new BadRequestException(parameter + ": geçerli bir koordinat değil", e);
        }
    }
}
