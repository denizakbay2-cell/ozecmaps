package com.citynavigator.routing;

/**
 * A place a traveller may want to stop at on the way: a fuel or charging station, a pharmacy, a bank or an ATM, a
 * market.
 */
public record RoadsidePlace(Kind kind, double lat, double lon, String name) {

    /**
     * What a traveller looks for along a route.
     */
    public enum Kind {
        FUEL, CHARGING, PHARMACY, MONEY, MARKET
    }
}
