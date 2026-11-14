package com.citynavigator.dto;

import java.util.List;

/**
 * A road on the traffic layer.
 *
 * @param factor share of the free-flow speed traffic keeps now (1 = free flow)
 * @param points the road's shape as {@code [lat, lon]} pairs
 */
public record TrafficRoadView(double factor, List<double[]> points) {
}
