package com.citynavigator.routing;

import java.util.Objects;

/**
 * Routing attributes of one OpenStreetMap way, already interpreted from its tags.
 *
 * @param name        street name (or road number), {@code null} when the way has none
 * @param maxSpeedKmh signposted limit, 0 when unknown; it can only lower the class speed
 * @param access      bits from {@link TravelMode#accessBits} saying who may travel which way
 */
public record RoadWay(long id, String name, RoadClass roadClass, int maxSpeedKmh, boolean roundabout, int access) {

    public RoadWay {
        Objects.requireNonNull(roadClass, "roadClass");
        if (maxSpeedKmh < 0) {
            throw new IllegalArgumentException("maxSpeedKmh must not be negative");
        }
    }

    public boolean allows(TravelMode mode, int direction) {
        return (access & mode.bit(direction)) != 0;
    }

    public boolean allowsAny(TravelMode mode) {
        return allows(mode, RoadGraph.FORWARD) || allows(mode, RoadGraph.BACKWARD);
    }

    public boolean hasName() {
        return name != null;
    }

    public double speedKmh(TravelMode mode) {
        int speed = roadClass.speedKmh(mode);
        if (speed == 0) {
            speed = mode.fallbackSpeedKmh();
        }
        return mode == TravelMode.CAR && maxSpeedKmh > 0 ? Math.min(speed, maxSpeedKmh) : speed;
    }

    /**
     * Whether two ways are the same road for a driver: the same name, or the same unnamed way.
     */
    public boolean sameRoadAs(RoadWay other) {
        return hasName() ? name.equals(other.name) : id == other.id;
    }
}
