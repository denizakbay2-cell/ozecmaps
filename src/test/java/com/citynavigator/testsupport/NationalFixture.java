package com.citynavigator.testsupport;

import com.citynavigator.routing.NationalGraph;
import com.citynavigator.routing.RoadGraph;
import com.citynavigator.routing.RoadWay;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * National graphs for tests, made of the same ways as a detailed {@link RoadGraph}: the node ids are the detailed
 * graph's node numbers, so both graphs describe exactly the same car roads.
 */
public final class NationalFixture {

    private NationalFixture() {
    }

    public static NationalGraph of(RoadGraph detailed) {
        return of(detailed, null);
    }

    public static NationalGraph of(RoadGraph detailed, Instant dataTimestamp) {
        NationalGraph.Builder builder = NationalGraph.builder().dataTimestamp(dataTimestamp);
        for (int node = 0; node < detailed.nodeCount(); node++) {
            builder.addNode(node, detailed.lat(node), detailed.lon(node));
        }
        Map<Long, TreeMap<Integer, Integer>> ways = new TreeMap<>();
        for (int segment = 0; segment < detailed.segmentCount(); segment++) {
            ways.computeIfAbsent(detailed.wayId(segment), id -> new TreeMap<>())
                    .put(detailed.sequence(segment), segment);
        }
        for (TreeMap<Integer, Integer> segments : ways.values()) {
            RoadWay way = detailed.way(segments.firstEntry().getValue());
            List<Long> refs = new ArrayList<>();
            for (int segment : segments.values()) {
                if (refs.isEmpty() || refs.getLast() != detailed.from(segment)) {
                    add(builder, way, refs);
                    refs.add((long) detailed.from(segment));
                }
                refs.add((long) detailed.to(segment));
            }
            add(builder, way, refs);
        }
        return builder.build();
    }

    private static void add(NationalGraph.Builder builder, RoadWay way, List<Long> refs) {
        if (refs.size() >= 2) {
            builder.addWay(way, refs.stream().mapToLong(Long::longValue).toArray());
        }
        refs.clear();
    }
}
