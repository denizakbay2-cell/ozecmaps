package com.citynavigator.routing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * A* on the {@link NationalGraph}. Works like {@link AStarRouter}, with one difference: an edge is a whole stretch of
 * road with a shape, so the start and destination sit somewhere along that shape ({@link EdgeSnap#offsetMeters()})
 * and the route follows the shape point by point. Thread-safe.
 */
public final class NationalRouter {

    private static final double ESTIMATE_SLACK = 0.999;
    private static final int FROM_START = -1;
    private static final double MIN_PIECE_METERS = 0.01;

    private final NationalGraph graph;

    public NationalRouter(NationalGraph graph) {
        this.graph = graph;
    }

    public Optional<Path> route(EdgeSnap start, EdgeSnap goal, Weighting weighting) {
        return new Search(start, goal, weighting, weighting).run();
    }

    /**
     * A route that is clearly different from {@code best} and not much worse, if there is one (see
     * {@link Alternatives}); it is described with the true costs of {@code weighting}.
     */
    public Optional<Path> alternative(EdgeSnap start, EdgeSnap goal, Weighting weighting, Path best) {
        return Alternatives.find(best, weighting, penalized -> new Search(start, goal, penalized, weighting).run());
    }

    /** Distance from the first shape point to each shape point of the edge. */
    double[] cumulative(int edge) {
        int count = graph.pointCount(edge);
        double[] along = new double[count];
        for (int i = 1; i < count; i++) {
            along[i] = along[i - 1] + GeoMath.distance(graph.pointLat(edge, i - 1), graph.pointLon(edge, i - 1),
                    graph.pointLat(edge, i), graph.pointLon(edge, i));
        }
        return along;
    }

    private final class Search {

        private final EdgeSnap start;
        private final EdgeSnap goal;
        /** What the search minimises. */
        private final Weighting weighting;
        /** What the route found costs and how long it takes; the same as {@link #weighting} but for alternatives. */
        private final Weighting pricing;
        private final double startLength;
        private final double goalLength;
        private final double[] cost;
        private final int[] parent;
        private final boolean[] settled;
        private final MinHeap open = new MinHeap(1024);
        private final int[] goalNodes = new int[2];
        private final int[] goalEntries = new int[2];
        private final double[] goalCosts = new double[2];
        private double best = Double.POSITIVE_INFINITY;
        private int bestGoal = -1;
        private int directEntry = -1;
        private int visited;

        Search(EdgeSnap start, EdgeSnap goal, Weighting weighting, Weighting pricing) {
            this.start = start;
            this.goal = goal;
            this.weighting = weighting;
            this.pricing = pricing;
            this.startLength = total(start.edge());
            this.goalLength = total(goal.edge());
            cost = new double[graph.nodeCount()];
            Arrays.fill(cost, Double.POSITIVE_INFINITY);
            parent = new int[graph.nodeCount()];
            settled = new boolean[graph.nodeCount()];
        }

        Optional<Path> run() {
            int forwardGoal = RoadGraph.entry(goal.edge(), RoadGraph.FORWARD);
            goalNodes[0] = graph.from(goal.edge());
            goalEntries[0] = forwardGoal;
            goalCosts[0] = partial(forwardGoal, share(goal.offsetMeters(), goalLength));
            goalNodes[1] = graph.to(goal.edge());
            goalEntries[1] = RoadGraph.reverse(forwardGoal);
            goalCosts[1] = partial(goalEntries[1], share(goalLength - goal.offsetMeters(), goalLength));
            considerSameEdge();
            int forwardStart = RoadGraph.entry(start.edge(), RoadGraph.FORWARD);
            leave(forwardStart, share(startLength - start.offsetMeters(), startLength));
            leave(RoadGraph.reverse(forwardStart), share(start.offsetMeters(), startLength));
            while (!open.isEmpty()) {
                double estimate = open.peekKey();
                int node = open.poll();
                if (settled[node]) {
                    continue;
                }
                if (estimate >= best) {
                    break;
                }
                settled[node] = true;
                visited++;
                for (int k = 0; k < 2; k++) {
                    if (node == goalNodes[k] && cost[node] + goalCosts[k] < best) {
                        best = cost[node] + goalCosts[k];
                        bestGoal = k;
                    }
                }
                expand(node);
            }
            if (Double.isInfinite(best)) {
                return Optional.empty();
            }
            return Optional.of(bestGoal < 0 ? directPath() : pathThrough(bestGoal));
        }

        private void considerSameEdge() {
            if (start.edge() != goal.edge()) {
                return;
            }
            boolean forward = goal.offsetMeters() >= start.offsetMeters();
            int entry = RoadGraph.entry(start.edge(), forward ? RoadGraph.FORWARD : RoadGraph.BACKWARD);
            double direct = partial(entry, share(Math.abs(goal.offsetMeters() - start.offsetMeters()), startLength));
            if (direct < best) {
                best = direct;
                directEntry = entry;
            }
        }

        private void leave(int entry, double fraction) {
            double partial = partial(entry, fraction);
            int node = graph.target(entry);
            if (partial < cost[node]) {
                cost[node] = partial;
                parent[node] = FROM_START;
                open.add(partial + estimate(node), node);
            }
        }

        private void expand(int node) {
            for (int i = graph.firstEntry(node); i < graph.endEntry(node); i++) {
                int entry = graph.entryAt(i);
                double step = weighting.cost(entry);
                int next = graph.target(entry);
                if (Double.isInfinite(step) || settled[next]) {
                    continue;
                }
                double candidate = cost[node] + step;
                if (candidate < cost[next]) {
                    cost[next] = candidate;
                    parent[next] = entry;
                    open.add(candidate + estimate(next), next);
                }
            }
        }

        private double estimate(int node) {
            double meters = GeoMath.distance(graph.lat(node), graph.lon(node), goal.point().lat(), goal.point().lon());
            return weighting.lowerBound(meters) * ESTIMATE_SLACK;
        }

        /** Standing on a tower costs nothing, even on an edge the car may not use. */
        private double partial(int entry, double fraction) {
            if (fraction <= 0) {
                return 0;
            }
            double whole = weighting.cost(entry);
            return Double.isInfinite(whole) ? whole : whole * fraction;
        }

        private Path pathThrough(int goalSide) {
            Deque<Integer> entries = new ArrayDeque<>();
            int node = goalNodes[goalSide];
            while (parent[node] != FROM_START) {
                entries.addFirst(parent[node]);
                node = graph.source(parent[node]);
            }
            int forwardStart = RoadGraph.entry(start.edge(), RoadGraph.FORWARD);
            boolean leftForward = node == graph.to(start.edge());
            Assembler path = new Assembler(start.point());
            path.along(leftForward ? forwardStart : RoadGraph.reverse(forwardStart), start.offsetMeters(),
                    leftForward ? Double.POSITIVE_INFINITY : 0);
            for (int entry : entries) {
                boolean forward = RoadGraph.directionOf(entry) == RoadGraph.FORWARD;
                path.along(entry, forward ? 0 : Double.POSITIVE_INFINITY, forward ? Double.POSITIVE_INFINITY : 0);
            }
            path.along(goalEntries[goalSide], goalSide == 0 ? 0 : Double.POSITIVE_INFINITY, goal.offsetMeters());
            return path.build(visited);
        }

        private Path directPath() {
            Assembler path = new Assembler(start.point());
            path.along(directEntry, start.offsetMeters(), goal.offsetMeters());
            return path.build(visited);
        }

        private double total(int edge) {
            double[] along = cumulative(edge);
            return along[along.length - 1];
        }

        private double share(double meters, double length) {
            return length <= 0 ? 0 : Math.clamp(meters / length, 0.0, 1.0);
        }

        /** Builds the polyline and pieces of a path by following edge shapes. */
        private final class Assembler {

            private final List<GeoPoint> points = new ArrayList<>();
            private final List<Integer> pointNodes = new ArrayList<>();
            private final List<Integer> entries = new ArrayList<>();
            private final List<Double> meters = new ArrayList<>();
            private final List<Double> seconds = new ArrayList<>();
            private final List<Double> freeFlowSeconds = new ArrayList<>();
            private double total;

            Assembler(GeoPoint first) {
                points.add(first);
                pointNodes.add(-1);
            }

            /**
             * Follows the entry's edge from one offset to another (offsets measured from the edge's first tower;
             * infinity means its second tower).
             */
            void along(int entry, double fromOffset, double toOffset) {
                int edge = RoadGraph.segmentOf(entry);
                double[] along = cumulative(edge);
                double length = along[along.length - 1];
                double from = Math.min(fromOffset, length);
                double to = Math.min(toOffset, length);
                if (RoadGraph.directionOf(entry) == RoadGraph.FORWARD) {
                    for (int i = 0; i < along.length; i++) {
                        if (along[i] > from && along[i] < to) {
                            add(entry, length, graph.point(edge, i), nodeAt(edge, i));
                        }
                    }
                } else {
                    for (int i = along.length - 1; i >= 0; i--) {
                        if (along[i] < from && along[i] > to) {
                            add(entry, length, graph.point(edge, i), nodeAt(edge, i));
                        }
                    }
                }
                int end = to <= 0 ? 0 : to >= length ? along.length - 1 : -1;
                add(entry, length, end >= 0 ? graph.point(edge, end) : pointAt(edge, along, to),
                        end >= 0 ? nodeAt(edge, end) : -1);
            }

            private void add(int entry, double edgeLength, GeoPoint to, int node) {
                GeoPoint previous = points.getLast();
                double length = GeoMath.distance(previous, to);
                if (length < MIN_PIECE_METERS) {
                    if (node >= 0) {
                        pointNodes.set(pointNodes.size() - 1, node);
                    }
                    return;
                }
                double fraction = edgeLength <= 0 ? 0 : length / edgeLength;
                points.add(to);
                pointNodes.add(node);
                entries.add(entry);
                meters.add(length);
                seconds.add(pricing.seconds(entry) * fraction);
                freeFlowSeconds.add(pricing.freeFlowSeconds(entry) * fraction);
                total += pricing.cost(entry) * fraction;
            }

            private int nodeAt(int edge, int index) {
                if (index == 0) {
                    return graph.from(edge);
                }
                return index == graph.pointCount(edge) - 1 ? graph.to(edge) : -1;
            }

            private GeoPoint pointAt(int edge, double[] along, double offset) {
                for (int i = 0; i + 1 < along.length; i++) {
                    if (offset <= along[i + 1]) {
                        double piece = along[i + 1] - along[i];
                        double fraction = piece <= 0 ? 0 : (offset - along[i]) / piece;
                        return GeoMath.interpolate(graph.pointLat(edge, i), graph.pointLon(edge, i),
                                graph.pointLat(edge, i + 1), graph.pointLon(edge, i + 1), fraction);
                    }
                }
                return graph.point(edge, along.length - 1);
            }

            Path build(int visitedNodes) {
                return new Path(points,
                        pointNodes.stream().mapToInt(Integer::intValue).toArray(),
                        entries.stream().mapToInt(Integer::intValue).toArray(),
                        meters.stream().mapToDouble(Double::doubleValue).toArray(),
                        seconds.stream().mapToDouble(Double::doubleValue).toArray(),
                        freeFlowSeconds.stream().mapToDouble(Double::doubleValue).toArray(),
                        total, visitedNodes);
            }
        }
    }
}
