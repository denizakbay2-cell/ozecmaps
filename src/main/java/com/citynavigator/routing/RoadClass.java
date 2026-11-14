package com.citynavigator.routing;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The OpenStreetMap {@code highway=*} values the engine routes on. The district speeds are typical for an Istanbul
 * district (traffic lights and congestion included, so far below the legal limits); zero means the mode may not use
 * the road unless its tags say otherwise. The national car speeds are free-flow speeds for the whole country;
 * congestion is added separately where traffic data exists.
 */
public enum RoadClass {

    //                              district speeds          national
    //                              car  bicycle  foot       car
    MOTORWAY("motorway",             80,   0,     0,         110),
    MOTORWAY_LINK("motorway_link",   45,   0,     0,          50),
    TRUNK("trunk",                   60,   0,     0,          90),
    TRUNK_LINK("trunk_link",         40,   0,     0,          45),
    PRIMARY("primary",               40,  16,     5,          70),
    PRIMARY_LINK("primary_link",     30,  16,     5,          40),
    SECONDARY("secondary",           35,  16,     5,          60),
    SECONDARY_LINK("secondary_link", 30,  16,     5,          35),
    TERTIARY("tertiary",             30,  16,     5,          50),
    TERTIARY_LINK("tertiary_link",   25,  16,     5,          30),
    UNCLASSIFIED("unclassified",     25,  16,     5,          45),
    RESIDENTIAL("residential",       20,  16,     5,          25),
    ROAD("road",                     20,  14,     5,          30),
    LIVING_STREET("living_street",   10,  12,     5,          10),
    SERVICE("service",               12,  14,     5,          15),
    TRACK("track",                    0,  12,     5,           0),
    PEDESTRIAN("pedestrian",          0,   6,     5,           0),
    CYCLEWAY("cycleway",              0,  18,     5,           0),
    FOOTWAY("footway",                0,   6,     5,           0),
    PATH("path",                      0,  12,     5,           0),
    STEPS("steps",                    0,   0,     3,           0);

    private static final Map<String, RoadClass> BY_OSM_VALUE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(RoadClass::osmValue, Function.identity()));

    private final String osmValue;
    private final int carKmh;
    private final int bicycleKmh;
    private final int footKmh;
    private final int nationalCarKmh;

    RoadClass(String osmValue, int carKmh, int bicycleKmh, int footKmh, int nationalCarKmh) {
        this.osmValue = osmValue;
        this.carKmh = carKmh;
        this.bicycleKmh = bicycleKmh;
        this.footKmh = footKmh;
        this.nationalCarKmh = nationalCarKmh;
    }

    public static Optional<RoadClass> fromOsm(String highway) {
        return Optional.ofNullable(highway == null ? null : BY_OSM_VALUE.get(highway));
    }

    public String osmValue() {
        return osmValue;
    }

    /** Free-flow car speed on the national graph. */
    public int nationalCarKmh() {
        return nationalCarKmh;
    }

    /** Whether the class belongs to the main network that the traffic layer shows from far away. */
    public boolean isMainRoad() {
        return ordinal() <= PRIMARY_LINK.ordinal();
    }

    public int speedKmh(TravelMode mode) {
        return switch (mode) {
            case CAR -> carKmh;
            case BICYCLE -> bicycleKmh;
            case FOOT -> footKmh;
        };
    }
}
