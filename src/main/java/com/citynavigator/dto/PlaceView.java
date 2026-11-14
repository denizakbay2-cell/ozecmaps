package com.citynavigator.dto;

/**
 * A place or business, from the application's own data or from the nationwide search.
 *
 * @param id       only for places stored by the application; {@code null} for nationwide search results
 * @param kind     what kind of business it is ("Eczane", "Kafe"), {@code null} for other places
 * @param detail   where it is ("Caferağa, Kadıköy, İstanbul"), when known
 * @param business whether it is shown on the map as a business
 */
public record PlaceView(Long id, String name, String category, String categoryLabel, String kind, String detail,
                        double lat, double lon, boolean fromOpenStreetMap, boolean business, String openingHours,
                        String phone, String website) {
}
