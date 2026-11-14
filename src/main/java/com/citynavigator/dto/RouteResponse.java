package com.citynavigator.dto;

import java.time.Instant;
import java.util.List;

/**
 * A planned route.
 *
 * @param engine              {@code astar} for the application's own engine in its detailed area, {@code national}
 *                            for its own engine on the car roads of all of Turkey, {@code osrm} for the nationwide
 *                            routing service
 * @param departAt            the departure the route was planned for; traffic is that of this time
 * @param trafficDelaySeconds how much longer the route takes than on empty roads because of traffic and incidents;
 *                            {@code null} when the engine does not know (nationwide service)
 * @param geometry            the route polyline as {@code [lat, lon]} pairs
 * @param traffic             the stretches where traffic or incidents leave less than 80 % of the free-flow speed;
 *                            empty when the engine does not know (nationwide service)
 * @param incidentIds         incidents on the route that slow it down (closed roads are already avoided)
 * @param visitedNodes        how many junctions the A* search settled; 0 for the nationwide service
 * @param alternatives        other routes between the same points, clearly different from this one and somewhat
 *                            slower, in the same form (without alternatives of their own); empty unless asked for
 */
public record RouteResponse(String mode, String optimize, String engine, double distanceMeters, double durationSeconds,
                            Instant departAt, Long trafficDelaySeconds, SnappedPoint from, SnappedPoint to,
                            List<double[]> geometry, List<TrafficStretch> traffic, List<InstructionView> instructions,
                            List<Long> incidentIds, int visitedNodes, List<RouteResponse> alternatives) {

    public RouteResponse {
        traffic = List.copyOf(traffic);
        alternatives = List.copyOf(alternatives);
    }

    public RouteResponse withAlternatives(List<RouteResponse> others) {
        return new RouteResponse(mode, optimize, engine, distanceMeters, durationSeconds, departAt, trafficDelaySeconds,
                from, to, geometry, traffic, instructions, incidentIds, visitedNodes, others);
    }
}
