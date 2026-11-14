package com.citynavigator.dto;

import java.time.LocalDateTime;

/**
 * How busy Istanbul is right now.
 *
 * @param index      Istanbul traffic index, 1 (open roads) to 99 (very heavy); {@code null} when unavailable
 * @param typical    the usual index at this day type and hour over the last four weeks
 * @param level      "akıcı", "normal", "yoğun" or "çok yoğun"
 * @param comparison compared to usual, for example "normalden yoğun"
 * @param at         Istanbul local time of the reading
 * @param cells      number of places (about 600 × 900 m) with typical hourly traffic data
 */
public record TrafficStatus(Integer index, Double typical, String level, String comparison, LocalDateTime at,
                            int cells, String source) {
}
