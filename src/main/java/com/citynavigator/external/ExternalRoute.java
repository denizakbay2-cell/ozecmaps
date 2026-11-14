package com.citynavigator.external;

import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.Instruction;
import java.util.List;

/**
 * A route from the external routing service, in the same terms as the application's own engine.
 *
 * @param points       the route polyline
 * @param instructions steps whose {@code pointIndex} refers to {@code points}
 */
public record ExternalRoute(double distanceMeters, double durationSeconds, List<GeoPoint> points,
                            List<Instruction> instructions, Waypoint from, Waypoint to) {

    public ExternalRoute {
        points = List.copyOf(points);
        instructions = List.copyOf(instructions);
    }

    /**
     * Where the service placed a requested point on its road network.
     *
     * @param name           the road's name, {@code null} if it has none
     * @param distanceMeters how far the requested point was from that road
     */
    public record Waypoint(String name, GeoPoint point, double distanceMeters) {
    }
}
