package com.citynavigator.external;

/**
 * A search result from the geocoder.
 *
 * @param osmRef  for example {@code node/2445263840}
 * @param osmKey  main OpenStreetMap key of the object, for example {@code shop}
 * @param detail  where it is, for example "Neşet Ömer Sokağı, Caferağa, Kadıköy, İstanbul"
 */
public record GeocodedPlace(String name, String osmRef, String osmKey, String osmValue, double lat, double lon,
                            String detail) {
}
