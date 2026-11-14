package com.citynavigator.routing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What road incidents do to the network right now: closed segments and slowed-down segments. Immutable; a change
 * produces a new instance, so a route search always sees one consistent picture while incidents are updated.
 */
public final class TrafficConditions {

    public static final TrafficConditions NONE = new TrafficConditions(Map.of());

    private final Map<Integer, List<Restriction>> bySegment;

    private TrafficConditions(Map<Integer, List<Restriction>> bySegment) {
        this.bySegment = bySegment;
    }

    /**
     * Adds (or replaces) one incident's effect on the given segments.
     */
    public TrafficConditions with(Restriction restriction, Collection<Integer> segments) {
        Map<Integer, List<Restriction>> copy = copyWithout(restriction.incidentId());
        for (int segment : segments) {
            copy.computeIfAbsent(segment, key -> new ArrayList<>()).add(restriction);
        }
        return new TrafficConditions(freeze(copy));
    }

    public TrafficConditions without(long incidentId) {
        return new TrafficConditions(freeze(copyWithout(incidentId)));
    }

    /**
     * 1 is free flow, 0 means the segment is closed. Closures stop cars and bicycles; slowdowns only affect cars;
     * walkers are never affected.
     */
    public double speedFactor(int segment, TravelMode mode, Instant at) {
        List<Restriction> restrictions = bySegment.get(segment);
        if (restrictions == null || mode == TravelMode.FOOT) {
            return 1;
        }
        double factor = 1;
        for (Restriction restriction : restrictions) {
            if (!restriction.activeAt(at)) {
                continue;
            }
            if (restriction.closed()) {
                return 0;
            }
            if (mode == TravelMode.CAR) {
                factor = Math.min(factor, restriction.speedFactor());
            }
        }
        return factor;
    }

    public Set<Long> incidentsOn(int segment, Instant at) {
        Set<Long> incidents = new LinkedHashSet<>();
        for (Restriction restriction : bySegment.getOrDefault(segment, List.of())) {
            if (restriction.activeAt(at)) {
                incidents.add(restriction.incidentId());
            }
        }
        return incidents;
    }

    public int restrictedSegmentCount() {
        return bySegment.size();
    }

    private Map<Integer, List<Restriction>> copyWithout(long incidentId) {
        Map<Integer, List<Restriction>> copy = new HashMap<>();
        bySegment.forEach((segment, restrictions) -> {
            List<Restriction> kept = new ArrayList<>();
            for (Restriction restriction : restrictions) {
                if (restriction.incidentId() != incidentId) {
                    kept.add(restriction);
                }
            }
            if (!kept.isEmpty()) {
                copy.put(segment, kept);
            }
        });
        return copy;
    }

    private static Map<Integer, List<Restriction>> freeze(Map<Integer, List<Restriction>> map) {
        Map<Integer, List<Restriction>> frozen = new HashMap<>();
        map.forEach((segment, restrictions) -> frozen.put(segment, List.copyOf(restrictions)));
        return Map.copyOf(frozen);
    }

    /**
     * One incident's effect: either the road is closed, or traffic moves at {@code speedFactor} of normal speed.
     *
     * @param until when the effect ends by itself; {@code null} means until the incident is resolved
     */
    public record Restriction(long incidentId, boolean closed, double speedFactor, Instant until) {

        public Restriction {
            if (!closed && !(speedFactor > 0 && speedFactor <= 1)) {
                throw new IllegalArgumentException("speedFactor must be in (0, 1]: " + speedFactor);
            }
        }

        public static Restriction closure(long incidentId, Instant until) {
            return new Restriction(incidentId, true, 0, until);
        }

        public static Restriction slowdown(long incidentId, double speedFactor, Instant until) {
            return new Restriction(incidentId, false, speedFactor, until);
        }

        boolean activeAt(Instant at) {
            return until == null || at.isBefore(until);
        }
    }
}
