package com.citynavigator.dto;

import java.time.Instant;
import java.util.List;

/**
 * A road incident with the road pieces it covers.
 *
 * @param endsAt {@code null} when it lasts until resolved
 * @param lines  the covered segments, each as {@code [[lat, lon], [lat, lon]]}
 */
public record IncidentView(long id, String type, String typeLabel, String status, String description, String street,
                           double lat, double lon, Instant reportedAt, Instant endsAt, List<List<double[]>> lines) {
}
