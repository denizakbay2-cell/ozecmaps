package com.citynavigator.entity;

import com.citynavigator.exception.BusinessRuleException;

/**
 * Shared checks for free-text fields and coordinates.
 */
final class Texts {

    private Texts() {
    }

    static String required(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(field + " boş olamaz");
        }
        return limited(value.strip(), field, maxLength);
    }

    static String optional(String value, String field, int maxLength) {
        return value == null || value.isBlank() ? null : limited(value.strip(), field, maxLength);
    }

    static void checkCoordinate(double lat, double lon) {
        if (!(lat >= -90 && lat <= 90) || !(lon >= -180 && lon <= 180)) {
            throw new BusinessRuleException("Geçersiz koordinat: " + lat + ", " + lon);
        }
    }

    private static String limited(String value, String field, int maxLength) {
        if (value.length() > maxLength) {
            throw new BusinessRuleException(field + " en fazla " + maxLength + " karakter olabilir");
        }
        return value;
    }
}
