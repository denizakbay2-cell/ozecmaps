package com.citynavigator.routing;

/**
 * The congestion a route search assumes: the typical profile at the departure time, scaled by how much busier or
 * calmer the city is right now than usual ({@code liveAdjustment} 1 means as usual).
 */
public record RoadTraffic(CongestionProfile profile, DayTime when, double liveAdjustment) {

    public static final RoadTraffic NONE = new RoadTraffic(CongestionProfile.NONE, new DayTime(DayTime.WEEKDAY, 0), 1);
    /** Even a jam moves a little; this also keeps travel times finite. */
    static final double SLOWEST = 0.1;

    /**
     * Whether traffic data exists for the point.
     */
    public boolean covers(double lat, double lon) {
        return !Double.isNaN(profile.factor(lat, lon, when));
    }

    /**
     * Share of the free-flow speed at the point: 1 where there is no data.
     */
    public double factor(double lat, double lon) {
        double typical = profile.factor(lat, lon, when);
        if (Double.isNaN(typical)) {
            return 1;
        }
        return Math.clamp(typical * liveAdjustment, SLOWEST, 1.0);
    }
}
