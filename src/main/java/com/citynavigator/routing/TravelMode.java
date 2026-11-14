package com.citynavigator.routing;

/**
 * How the traveller moves. Each mode has its own access bits in {@link RoadWay#access()} and speeds in
 * {@link RoadClass}. {@code maxSpeedKmh} must be at least every speed the mode can reach, because A* divides the
 * straight-line distance by it to get a cost that never overestimates.
 */
public enum TravelMode {

    CAR(0b0_0001, 0b0_0010, 80, 10),
    BICYCLE(0b0_0100, 0b0_1000, 18, 6),
    FOOT(0b1_0000, 0b1_0000, 5, 4);

    private final int forwardBit;
    private final int backwardBit;
    private final int maxSpeedKmh;
    private final int fallbackSpeedKmh;

    TravelMode(int forwardBit, int backwardBit, int maxSpeedKmh, int fallbackSpeedKmh) {
        this.forwardBit = forwardBit;
        this.backwardBit = backwardBit;
        this.maxSpeedKmh = maxSpeedKmh;
        this.fallbackSpeedKmh = fallbackSpeedKmh;
    }

    /**
     * The access bit for travelling a way {@link RoadGraph#FORWARD forward} or {@link RoadGraph#BACKWARD backward}.
     * Walkers use one bit for both directions: one-way streets do not apply to them.
     */
    public int bit(int direction) {
        return direction == RoadGraph.FORWARD ? forwardBit : backwardBit;
    }

    public int accessBits(boolean forward, boolean backward) {
        return (forward ? forwardBit : 0) | (backward ? backwardBit : 0);
    }

    public int maxSpeedKmh() {
        return maxSpeedKmh;
    }

    /**
     * Speed on a road the tags open to this mode although its class normally is not (a track open to cars).
     */
    int fallbackSpeedKmh() {
        return fallbackSpeedKmh;
    }
}
