package com.citynavigator.dto;

import java.time.Instant;
import java.util.List;

/**
 * One part of a public transport journey: a walk or a ride.
 *
 * @param kind     {@code WALK}, or what is ridden: {@code FERRY}, {@code METRO}, {@code RAIL}, {@code TRAM},
 *                 {@code FUNICULAR}, {@code CABLE_CAR}, {@code BUS}, {@code METROBUS}, {@code MINIBUS},
 *                 {@code DOLMUS}
 * @param label    what the kind is called ("Vapur", "Yürüyüş")
 * @param line     the number or name of the line ("M4", "14M", "Marmaray"); {@code null} for walks, ferries,
 *                 minibuses and dolmuşes
 * @param lineName what the line connects ("Kadıköy - Tavşantepe"); {@code null} for walks
 * @param headsign where the vehicle is heading; {@code null} for walks
 * @param from     the stop the leg starts at; {@code null} for the start of the journey
 * @param to       the stop the leg ends at; {@code null} for the destination
 * @param stops    the stops the vehicle calls at after boarding, the last one included; 0 for walks
 * @param geometry {@code [lat, lon]} points: the way the vehicle goes (its line when the timetable has one, otherwise
 *                 the stops it calls at), or the two ends of a walk
 */
public record TransitLegView(String kind, String label, String line, String lineName, String headsign, String from,
                             String to, Instant departAt, Instant arriveAt, long durationSeconds, long distanceMeters,
                             int stops, List<double[]> geometry) {
}
