package com.citynavigator.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Turns a path into turn-by-turn directions, the way a driver would want them: a step where the road changes name
 * at a junction or where the route leaves the natural continuation of the road, roundabouts as one step with an
 * exit number, and nothing where the road merely bends, where it gets its name back after a bridge or a tunnel, or
 * in the last metres before the destination.
 */
public final class InstructionBuilder {

    /** Directions are measured over this distance before and after a point, so tiny kinks do not count. */
    static final double BEARING_SAMPLE_METERS = 25;
    /** A turn that keeps the street name is announced only from this angle on. */
    static final double MIN_TURN_DEGREES = 30;
    /** Turns this close in the same direction or onto the same street (a divided road) are announced as one. */
    static final double MERGE_METERS = 30;
    /** A turn this close to the destination is part of arriving, not a step of its own. */
    static final double ARRIVAL_METERS = 30;

    private final RoutingGraph graph;

    public InstructionBuilder(RoutingGraph graph) {
        this.graph = graph;
    }

    public List<Instruction> build(Path path, TravelMode mode) {
        if (path.pieceCount() == 0) {
            return List.of(new Instruction(Maneuver.ARRIVE, null, null, 0, 0, 0, 0, 0));
        }
        boolean roundabouts = mode != TravelMode.FOOT;
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(0, Maneuver.DEPART, wayOf(path, 0), 0, bearingAfter(path, 0)));
        int roundaboutStart = -1;
        int exitsPassed = 0;
        for (int k = 1; k < path.pieceCount(); k++) {
            RoadWay in = wayOf(path, k - 1);
            RoadWay out = wayOf(path, k);
            int node = path.pointNode(k);
            if (roundabouts && out.roundabout()) {
                if (!in.roundabout()) {
                    roundaboutStart = k;
                    exitsPassed = 0;
                } else if (node >= 0) {
                    exitsPassed += exitsAt(node, path.pieceEntry(k - 1), path.pieceEntry(k), mode);
                }
                continue;
            }
            if (roundabouts && in.roundabout() && roundaboutStart >= 0) {
                steps.add(new Step(roundaboutStart, Maneuver.ROUNDABOUT, out, exitsPassed + 1, 0));
                roundaboutStart = -1;
                continue;
            }
            if (node >= 0) {
                Step turn = turnAt(path, k, node, in, out, mode);
                if (turn != null) {
                    steps.add(turn);
                }
            }
        }
        steps.add(new Step(path.pointCount() - 1, Maneuver.ARRIVE, null, 0, 0));
        return withDistances(path, merged(path, steps));
    }

    private Step turnAt(Path path, int k, int node, RoadWay in, RoadWay out, TravelMode mode) {
        int entryIn = path.pieceEntry(k - 1);
        int entryOut = path.pieceEntry(k);
        double bearingIn = bearingBefore(path, k);
        double angle = GeoMath.turnAngle(bearingIn, bearingAfter(path, k));
        double ourTurn = Math.abs(GeoMath.turnAngle(bearingIn, graph.initialBearing(entryOut)));

        boolean hasAlternative = false;
        boolean straighterAlternative = false;
        for (int i = graph.firstEntry(node); i < graph.endEntry(node); i++) {
            int entry = graph.entryAt(i);
            if (entry == entryOut || entry == RoadGraph.reverse(entryIn) || !graph.allows(entry, mode)) {
                continue;
            }
            hasAlternative = true;
            if (Math.abs(GeoMath.turnAngle(bearingIn, graph.initialBearing(entry))) < ourTurn) {
                straighterAlternative = true;
            }
        }
        if (!hasAlternative) {
            return null;
        }
        boolean renamed = (in.hasName() || out.hasName()) && !Objects.equals(in.name(), out.name());
        boolean leavesTheRoad = Math.abs(angle) >= MIN_TURN_DEGREES && straighterAlternative;
        return renamed || leavesTheRoad ? new Step(k, Maneuver.ofAngle(angle), out, 0, 0) : null;
    }

    /** Roads leaving a roundabout node, other than the roundabout itself and the road we came in on. */
    private int exitsAt(int node, int entryIn, int entryOut, TravelMode mode) {
        int exits = 0;
        for (int i = graph.firstEntry(node); i < graph.endEntry(node); i++) {
            int entry = graph.entryAt(i);
            if (entry != entryOut && entry != RoadGraph.reverse(entryIn) && graph.allows(entry, mode)
                    && !graph.roadOf(entry).roundabout()) {
                exits++;
            }
        }
        return exits;
    }

    private List<Step> merged(Path path, List<Step> steps) {
        List<Step> result = new ArrayList<>();
        int end = path.pointCount() - 1;
        for (Step step : steps) {
            Step previous = result.isEmpty() ? null : result.getLast();
            if (step.isTurn() && path.metersBetween(step.pointIndex(), end) < ARRIVAL_METERS) {
                continue;
            }
            if (previous != null && step.maneuver() == Maneuver.CONTINUE && previous.road() != null
                    && previous.road().hasName() && previous.road().sameRoadAs(step.road())) {
                // the road had another name for a stretch (a bridge, a tunnel) and got its name back
                continue;
            }
            if (previous != null && belongTogether(path, previous, step)) {
                double angle = GeoMath.turnAngle(bearingBefore(path, previous.pointIndex()),
                        bearingAfter(path, step.pointIndex()));
                result.set(result.size() - 1,
                        new Step(previous.pointIndex(), Maneuver.ofAngle(angle), step.road(), 0, 0));
            } else {
                result.add(step);
            }
        }
        return result;
    }

    /**
     * Two quick turns that are really one: crossing both halves of a divided road (same street twice), or a turn
     * split over two nearby junctions (same direction twice). A right-then-left jog stays two steps.
     */
    private static boolean belongTogether(Path path, Step first, Step second) {
        if (!first.isTurn() || !second.isTurn()
                || path.metersBetween(first.pointIndex(), second.pointIndex()) >= MERGE_METERS) {
            return false;
        }
        boolean sameStreet = first.road().hasName() && first.road().sameRoadAs(second.road());
        boolean sameDirection = first.side() != 0 && first.side() == second.side();
        return sameStreet || sameDirection;
    }

    private static List<Instruction> withDistances(Path path, List<Step> steps) {
        List<Instruction> instructions = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            int next = i + 1 < steps.size() ? steps.get(i + 1).pointIndex() : step.pointIndex();
            RoadWay road = step.road();
            instructions.add(new Instruction(step.maneuver(), road == null ? null : road.name(),
                    road == null ? null : road.roadClass(), step.exitNumber(), step.pointIndex(), step.bearing(),
                    path.metersBetween(step.pointIndex(), next), path.secondsBetween(step.pointIndex(), next)));
        }
        return List.copyOf(instructions);
    }

    private RoadWay wayOf(Path path, int piece) {
        return graph.roadOf(path.pieceEntry(piece));
    }

    private static double bearingBefore(Path path, int point) {
        return GeoMath.bearing(pointAlong(path, point, -BEARING_SAMPLE_METERS), path.point(point));
    }

    private static double bearingAfter(Path path, int point) {
        return GeoMath.bearing(path.point(point), pointAlong(path, point, BEARING_SAMPLE_METERS));
    }

    /**
     * The spot that lies the given distance along the route from a point (backwards when negative), or the end of
     * the route if it is closer.
     */
    private static GeoPoint pointAlong(Path path, int point, double meters) {
        double left = Math.abs(meters);
        if (meters >= 0) {
            for (int piece = point; piece < path.pieceCount(); piece++) {
                if (left <= path.pieceMeters(piece)) {
                    return between(path.point(piece), path.point(piece + 1), left / path.pieceMeters(piece));
                }
                left -= path.pieceMeters(piece);
            }
            return path.point(path.pointCount() - 1);
        }
        for (int piece = point - 1; piece >= 0; piece--) {
            if (left <= path.pieceMeters(piece)) {
                return between(path.point(piece + 1), path.point(piece), left / path.pieceMeters(piece));
            }
            left -= path.pieceMeters(piece);
        }
        return path.point(0);
    }

    private static GeoPoint between(GeoPoint from, GeoPoint to, double fraction) {
        return GeoMath.interpolate(from.lat(), from.lon(), to.lat(), to.lon(), fraction);
    }

    /** @param road the road taken at this step, {@code null} on arrival */
    private record Step(int pointIndex, Maneuver maneuver, RoadWay road, int exitNumber, double bearing) {

        boolean isTurn() {
            return maneuver != Maneuver.DEPART && maneuver != Maneuver.ARRIVE && maneuver != Maneuver.ROUNDABOUT;
        }

        /** -1 for a turn to the left, 1 to the right, 0 straight on or turning back. */
        int side() {
            return switch (maneuver) {
                case SLIGHT_LEFT, LEFT, SHARP_LEFT -> -1;
                case SLIGHT_RIGHT, RIGHT, SHARP_RIGHT -> 1;
                default -> 0;
            };
        }
    }
}
