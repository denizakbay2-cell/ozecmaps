package com.citynavigator.routing;

/**
 * One step of the turn-by-turn directions.
 *
 * @param street         the road taken at this point, {@code null} if it has no name
 * @param roadClass      the kind of road taken, to describe it when it has no name
 * @param exitNumber     for {@link Maneuver#ROUNDABOUT}: which exit to take, counting from 1
 * @param pointIndex     where on the route polyline the step happens
 * @param bearing        for {@link Maneuver#DEPART}: the initial compass direction
 * @param distanceMeters distance from this step to the next one
 * @param durationSeconds travel time from this step to the next one
 */
public record Instruction(Maneuver maneuver, String street, RoadClass roadClass, int exitNumber, int pointIndex,
                          double bearing, double distanceMeters, double durationSeconds) {
}
