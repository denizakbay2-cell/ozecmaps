package com.citynavigator.routing;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Which segments a road incident covers, starting from the point where it was reported.
 */
public final class RoadFootprint {

    private RoadFootprint() {
    }

    /**
     * Segments of the same road (same name, or the same unnamed way) that start within the radius of the point,
     * following the road in both directions and through its branches. Suits an accident or roadworks.
     */
    public static Set<Integer> alongRoad(RoadGraph graph, Snap snap, double radiusMeters, TravelMode mode) {
        int first = snap.segment();
        RoadWay road = graph.way(first);
        Set<Integer> covered = new LinkedHashSet<>();
        covered.add(first);
        Map<Integer, Double> reached = new HashMap<>();
        Deque<Integer> queue = new ArrayDeque<>();
        reach(graph.from(first), snap.fraction() * graph.length(first), reached, queue);
        reach(graph.to(first), (1 - snap.fraction()) * graph.length(first), reached, queue);
        while (!queue.isEmpty()) {
            int node = queue.poll();
            double distance = reached.get(node);
            if (distance >= radiusMeters) {
                continue;
            }
            for (int i = graph.firstEntry(node); i < graph.endEntry(node); i++) {
                int entry = graph.entryAt(i);
                int segment = RoadGraph.segmentOf(entry);
                if (!graph.allowsAny(segment, mode) || !graph.way(segment).sameRoadAs(road)) {
                    continue;
                }
                covered.add(segment);
                reach(graph.target(entry), distance + graph.length(segment), reached, queue);
            }
        }
        return covered;
    }

    /**
     * The stretch between the junctions on either side of the point, at most {@code maxMeters} each way. Suits a
     * closure: the block is shut, the crossing streets stay open.
     */
    public static Set<Integer> betweenJunctions(RoadGraph graph, Snap snap, double maxMeters, TravelMode mode) {
        Set<Integer> covered = new LinkedHashSet<>();
        int first = snap.segment();
        covered.add(first);
        walkToJunction(graph, first, graph.to(first), maxMeters, mode, covered);
        walkToJunction(graph, first, graph.from(first), maxMeters, mode, covered);
        return covered;
    }

    private static void walkToJunction(RoadGraph graph, int segment, int node, double maxMeters, TravelMode mode,
                                       Set<Integer> covered) {
        int current = segment;
        int at = node;
        double walked = 0;
        while (walked < maxMeters) {
            int next = -1;
            int ways = 0;
            for (int i = graph.firstEntry(at); i < graph.endEntry(at); i++) {
                int candidate = RoadGraph.segmentOf(graph.entryAt(i));
                if (candidate != current && graph.allowsAny(candidate, mode)) {
                    ways++;
                    next = candidate;
                }
            }
            // a junction (several ways on), a dead end, or back where we started
            if (ways != 1 || !covered.add(next)) {
                return;
            }
            walked += graph.length(next);
            at = graph.from(next) == at ? graph.to(next) : graph.from(next);
            current = next;
        }
    }

    private static void reach(int node, double distance, Map<Integer, Double> reached, Deque<Integer> queue) {
        Double known = reached.get(node);
        if (known == null || distance < known) {
            reached.put(node, distance);
            queue.add(node);
        }
    }
}
