package com.citynavigator.routing;

/**
 * What the traveller does at an instruction point.
 */
public enum Maneuver {

    DEPART,
    CONTINUE,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    U_TURN,
    ROUNDABOUT,
    ARRIVE;

    static final double STRAIGHT_DEGREES = 20;
    static final double SLIGHT_DEGREES = 50;
    static final double TURN_DEGREES = 125;
    static final double U_TURN_DEGREES = 165;

    /**
     * Classifies a change of direction from {@link GeoMath#turnAngle}: positive angles turn right.
     */
    public static Maneuver ofAngle(double angle) {
        double size = Math.abs(angle);
        if (size < STRAIGHT_DEGREES) {
            return CONTINUE;
        }
        if (size >= U_TURN_DEGREES) {
            return U_TURN;
        }
        boolean right = angle > 0;
        if (size < SLIGHT_DEGREES) {
            return right ? SLIGHT_RIGHT : SLIGHT_LEFT;
        }
        if (size < TURN_DEGREES) {
            return right ? RIGHT : LEFT;
        }
        return right ? SHARP_RIGHT : SHARP_LEFT;
    }
}
