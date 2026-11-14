package com.citynavigator.dto;

import java.time.Instant;
import java.util.List;

/**
 * The detailed area and how far the application reaches beyond it.
 *
 * @param bounds           {@code [minLat, minLon, maxLat, maxLon]} of the detailed area (own engine for all modes,
 *                         incidents, businesses on the map)
 * @param center           {@code [lat, lon]} where the map opens
 * @param dataTimestamp    when the OpenStreetMap data of the detailed area was taken
 * @param nationwide       whether routes and search also work in the rest of Turkey
 * @param nationwideBounds {@code [minLat, minLon, maxLat, maxLon]} of that wider area
 * @param nationalGraph    the car road network of Turkey the own engine uses; {@code null} when it is not loaded
 * @param trafficCells     places (about 600 × 900 m in Istanbul) with usual hourly traffic data
 */
public record MapInfo(String name, List<Double> bounds, List<Double> center, int nodes, int segments, int ways,
                      Instant dataTimestamp, Instant importedAt, String attribution, boolean nationwide,
                      List<Double> nationwideBounds, NationalGraphInfo nationalGraph, int trafficCells) {
}
