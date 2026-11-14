package com.citynavigator.dto;

import java.time.Instant;

/**
 * The car road network of Turkey the own engine routes on outside the detailed area.
 *
 * @param junctions     junctions and dead ends
 * @param roads         road pieces between them
 * @param dataTimestamp when the OpenStreetMap data was taken
 */
public record NationalGraphInfo(int junctions, int roads, int ways, Instant dataTimestamp) {
}
