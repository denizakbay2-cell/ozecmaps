package com.citynavigator.dto;

/**
 * A place along a planned route, for example a fuel station that could be added as a stop.
 *
 * @param kind         what it is ("Eczane", "Akaryakıt istasyonu")
 * @param detail       the street it is on, inside the detailed area; {@code null} elsewhere
 * @param alongMeters  how far from the start of the route it is reached
 * @param offsetMeters how far it lies from the route there
 * @param side         {@code RIGHT} or {@code LEFT} of the direction of travel
 */
public record AlongPlaceView(String name, String kind, String detail, double lat, double lon, long alongMeters,
                             long offsetMeters, String side) {
}
