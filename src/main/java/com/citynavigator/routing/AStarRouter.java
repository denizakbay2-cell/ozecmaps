package com.citynavigator.routing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * A* search between two snapped points. The start and destination usually lie in the middle of a segment, so the
 * search begins at both ends of the start segment (with the partial cost of getting there) and finishes through
 * either end of the destination segment. The estimate is the straight-line distance priced by
 * {@link Weighting#lowerBound}, which never overestimates; so the first time the cheapest open node costs at least
 * as much as the best complete route, that route is optimal.
 *
 * <p>Thread-safe: every search works on its own arrays and the graph is immutable.
 */
public final class AStarRouter {

    /** Keeps the estimate admissible despite rounding in partial-segment lengths. */
    private static final double ESTIMATE_SLACK = 0.999;
    private static final int FROM_START = -1;
    private static final double MIN_PIECE_METERS = 0.01;

    private final RoadGraph graph;

    public AStarRouter(RoadGraph graph) {
        this.graph = graph;
    }

    public Optional<Path> route(Snap start, Snap destination, Weighting weighting) {
        return new Search(start, destination, weighting, weighting).run();
    }

    /**
     * A route that is clearly different from {@code best} and not much worse, if there is one (see
     * {@link Alternatives}); it is described with the true costs of {@code weighting}.
     */
    public Optional<Path> alternative(Snap start, Snap destination, Weighting weighting, Path best) {
        return Alternatives.find(best, weighting,
                penalized -> new Search(start, destination, penalized, weighting).run());
    }

    private final class Search {

        private final Snap start;
        private final Snap goal;
        /** What the search minimises. */
        private final Weighting weighting;
        /** What the route found costs and how long it takes; the same as {@link #weighting} but for alternatives. */
        private final Weighting pricing;
        private final double[] cost;
        private final int[] parent;
        private final boolean[] settled;
        private final MinHeap open = new MinHeap(256);
        private final int[] goalNodes = new int[2];
        private final int[] goalEntries = new int[2];
        private final double[] goalCosts = new double[2];
        private double best = Double.POSITIVE_INFINITY;
        private int bestGoal = -1;
        private int directEntry = -1;
        private int visited;

        Search(Snap start, Snap goal, Weighting weighting, Weighting pricing) {
            this.start = start;
            this.goal = goal;
            this.weighting = weighting;
            this.pricing = pricing;
            cost = new double[graph.nodeCount()];
            Arrays.fill(cost, Double.POSITIVE_INFINITY);
            parent = new int[graph.nodeCount()];
            settled = new boolean[graph.nodeCount()];
        }

        Optional<Path> run() {
            prepareGoal();
            considerSameSegment();
            leaveStart(RoadGraph.entry(start.segment(), RoadGraph.FORWARD), 1 - start.fraction());
            leaveStart(RoadGraph.entry(start.segment(), RoadGraph.BACKWARD), start.fraction());
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
                reachGoal(node);
                expand(node);
            }
            if (Double.isInfinite(best)) {
                return Optional.empty();
            }
            return Optional.of(bestGoal < 0 ? directPath() : pathThrough(bestGoal));
        }

        /** The destination is reached from its segment's first node going forward, or its second going back. */
        private void prepareGoal() {
            int segment = goal.segment();
            goalNodes[0] = graph.from(segment);
            goalEntries[0] = RoadGraph.entry(segment, RoadGraph.FORWARD);
            goalCosts[0] = partialCost(goalEntries[0], goal.fraction());
            goalNodes[1] = graph.to(segment);
            goalEntries[1] = RoadGraph.entry(segment, RoadGraph.BACKWARD);
            goalCosts[1] = partialCost(goalEntries[1], 1 - goal.fraction());
        }

        /** Start and destination on the same segment: driving straight along it may be the whole route. */
        private void considerSameSegment() {
            if (start.segment() != goal.segment()) {
                return;
            }
            boolean forward = goal.fraction() >= start.fraction();
            int entry = RoadGraph.entry(start.segment(), forward ? RoadGraph.FORWARD : RoadGraph.BACKWARD);
            double direct = partialCost(entry, Math.abs(goal.fraction() - start.fraction()));
            if (direct < best) {
                best = direct;
                directEntry = entry;
            }
        }

        private void leaveStart(int entry, double fraction) {
            double partial = partialCost(entry, fraction);
            int node = graph.target(entry);
            if (partial < cost[node]) {
                cost[node] = partial;
                parent[node] = FROM_START;
                open.add(partial + estimate(node), node);
            }
        }

        private void reachGoal(int node) {
            for (int k = 0; k < 2; k++) {
                if (node == goalNodes[k] && cost[node] + goalCosts[k] < best) {
                    best = cost[node] + goalCosts[k];
                    bestGoal = k;
                }
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

        /** Standing on a node (fraction 0) costs nothing, even on a segment this mode may not use. */
        private double partialCost(int entry, double fraction) {
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
            int forwardStart = RoadGraph.entry(start.segment(), RoadGraph.FORWARD);
            boolean leftForward = node == graph.target(forwardStart);
            PathBuilder path = new PathBuilder(start.point());
            path.add(leftForward ? forwardStart : RoadGraph.reverse(forwardStart),
                    leftForward ? 1 - start.fraction() : start.fraction(), node);
            for (int entry : entries) {
                path.add(entry, 1, graph.target(entry));
            }
            path.addLast(goalEntries[goalSide], goalSide == 0 ? goal.fraction() : 1 - goal.fraction(), goal.point());
            return path.build(visited);
        }

        private Path directPath() {
            PathBuilder path = new PathBuilder(start.point());
            path.addLast(directEntry, Math.abs(goal.fraction() - start.fraction()), goal.point());
            return path.build(visited);
        }

        private final class PathBuilder {

            private final List<GeoPoint> points = new ArrayList<>();
            private final List<Integer> pointNodes = new ArrayList<>();
            private final List<Integer> entries = new ArrayList<>();
            private final List<Double> meters = new ArrayList<>();
            private final List<Double> seconds = new ArrayList<>();
            private final List<Double> freeFlowSeconds = new ArrayList<>();
            private double total;

            PathBuilder(GeoPoint first) {
                points.add(first);
                pointNodes.add(-1);
            }

            void add(int entry, double fraction, int toNode) {
                append(entry, fraction, graph.point(toNode), toNode);
            }

            void addLast(int entry, double fraction, GeoPoint destination) {
                append(entry, fraction, destination, -1);
            }

            private void append(int entry, double fraction, GeoPoint to, int toNode) {
                double length = graph.length(RoadGraph.segmentOf(entry)) * fraction;
                if (length < MIN_PIECE_METERS) {
                    // the point is (practically) where the path already is; remember the node it stands on
                    if (toNode >= 0) {
                        pointNodes.set(pointNodes.size() - 1, toNode);
                    }
                    return;
                }
                points.add(to);
                pointNodes.add(toNode);
                entries.add(entry);
                meters.add(length);
                seconds.add(pricing.seconds(entry) * fraction);
                freeFlowSeconds.add(pricing.freeFlowSeconds(entry) * fraction);
                total += pricing.cost(entry) * fraction;
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
