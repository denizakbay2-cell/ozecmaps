package com.citynavigator.dto;

/**
 * One step of the directions; {@code text} is ready to show, {@code maneuver} picks the icon.
 *
 * @param pointIndex     position of the step in the route geometry
 * @param distanceMeters distance to the next step
 */
public record InstructionView(String maneuver, String text, String street, int exit, int pointIndex,
                              double lat, double lon, double distanceMeters, double durationSeconds) {
}
