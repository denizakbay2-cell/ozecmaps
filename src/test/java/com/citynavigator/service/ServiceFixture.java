package com.citynavigator.service;

import static com.citynavigator.testsupport.GraphFixture.ALL;
import static com.citynavigator.testsupport.GraphFixture.FOOT_ONLY;

import com.citynavigator.routing.RoadClass;
import com.citynavigator.routing.RoadGraph;
import com.citynavigator.testsupport.GraphFixture;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

/**
 * A small town for the service tests.
 * <pre>
 *                 4  (Yan Sokak goes north from 2)
 *                 |
 *   1 ---------- 2 ---------- 3     Ana Cadde (primary), 200 m
 *
 *   footpath 5 - 6 two rows south; "Ada Yolu" 8 - 9 far away, not connected
 * </pre>
 */
final class ServiceFixture {

    static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private ServiceFixture() {
    }

    static RoadGraph town() {
        return new GraphFixture()
                .node(1, 0, 0).node(2, 0, 1).node(3, 0, 2).node(4, 1, 1)
                .node(5, -2, 0).node(6, -2, 2)
                .node(8, 5, 5).node(9, 5, 6)
                .way(10, "Ana Cadde", RoadClass.PRIMARY, false, ALL, 1, 2, 3)
                .way(11, "Yan Sokak", RoadClass.RESIDENTIAL, false, ALL, 2, 4)
                .way(12, null, RoadClass.FOOTWAY, false, FOOT_ONLY, 5, 6)
                .way(13, "Ada Yolu", RoadClass.RESIDENTIAL, false, ALL, 8, 9)
                .build();
    }

    static RoutingEngine.Snapshot snapshot() {
        return RoutingEngine.Snapshot.of(town(), com.citynavigator.routing.TrafficConditions.NONE);
    }
}
