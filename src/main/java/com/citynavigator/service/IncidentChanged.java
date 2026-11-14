package com.citynavigator.service;

import com.citynavigator.routing.TrafficConditions;
import java.util.List;

/**
 * Fired when an incident is reported or resolved. The routing engine applies it after the transaction has
 * committed, so routes never avoid a road because of a change that was rolled back.
 *
 * @param restriction the incident's effect, {@code null} when it was resolved
 * @param segments    graph segments it covers (empty when resolved)
 */
public record IncidentChanged(long incidentId, TrafficConditions.Restriction restriction, List<Integer> segments) {

    public IncidentChanged {
        segments = List.copyOf(segments);
    }

    static IncidentChanged resolved(long incidentId) {
        return new IncidentChanged(incidentId, null, List.of());
    }

    boolean active() {
        return restriction != null;
    }
}
