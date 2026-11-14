package com.citynavigator.entity;

import java.util.regex.Pattern;

/**
 * What a business adds to a place: what kind of business it is and how to reach it. Imported data is cleaned rather
 * than rejected: a website that is not http(s) or an over-long value is simply left out.
 *
 * @param kind         Turkish name of the kind of business, for example "Eczane" or "Kafe"
 * @param openingHours in OpenStreetMap notation, for example "Mo-Sa 09:00-19:00"
 */
public record PlaceDetails(String kind, String openingHours, String phone, String website) {

    public static final PlaceDetails NONE = new PlaceDetails(null, null, null, null);

    private static final Pattern WEB_ADDRESS = Pattern.compile("^https?://[^\\s<>\"]+$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_NUMBER = Pattern.compile("^\\+?[0-9 ()./-]{5,40}$");

    public PlaceDetails {
        kind = limited(kind, 60);
        openingHours = limited(openingHours, 255);
        phone = firstPhone(phone);
        website = webAddress(website);
    }

    private static String limited(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.length() > maxLength ? null : trimmed;
    }

    /** OpenStreetMap allows several numbers separated by ";"; the first one is enough. */
    private static String firstPhone(String value) {
        String first = value == null ? null : limited(value.split(";")[0], 60);
        return first != null && PHONE_NUMBER.matcher(first).matches() ? first : null;
    }

    private static String webAddress(String value) {
        String address = limited(value, 255);
        return address != null && WEB_ADDRESS.matcher(address).matches() ? address : null;
    }
}
