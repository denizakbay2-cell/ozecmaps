package com.citynavigator.dto;

import java.time.Instant;
import java.util.List;

/**
 * A journey by public transport: when to leave, when it arrives, and its walks and rides.
 *
 * @param mode         always {@code transit}, so that the client can tell it from a route
 * @param rides        how many vehicles it takes; 0 when walking is the better way
 * @param walkMeters   how far it walks in all (straight line)
 * @param source       where the timetables come from
 * @param alternatives other journeys worth a look: faster with more changes, or with fewer changes; none of them has
 *                     alternatives of its own
 */
public record TransitResponse(String mode, Instant departAt, Instant arriveAt, long durationSeconds, int rides,
                              long walkMeters, List<TransitLegView> legs, String source,
                              List<TransitResponse> alternatives) {
}
