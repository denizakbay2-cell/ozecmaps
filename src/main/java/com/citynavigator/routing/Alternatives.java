package com.citynavigator.routing;

import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * A second route next to the best one, found with the penalty method: the roads of the best route are made dearer
 * and the search runs again, so it leaves the best route wherever another way costs less than the penalty. The
 * result counts only if it is clearly different (runs along at most {@link #MAX_SHARED} of the best route) and still
 * good (costs at most {@link #MAX_STRETCH} times as much). A mild penalty is tried first; if its route is too
 * similar, a stronger one pushes the search further off.
 */
final class Alternatives {

    /** The alternative may run along at most this share of the best route's length. */
    static final double MAX_SHARED = 0.75;
    /** The alternative may cost at most this many times what the best route costs. */
    static final double MAX_STRETCH = 1.3;
    private static final List<Double> PENALTIES = List.of(1.4, 2.0);

    private Alternatives() {
    }

    /**
     * @param search runs the router with the weighting it is given and prices the result with the true weighting
     */
    static Optional<Path> find(Path best, Weighting weighting, Function<Weighting, Optional<Path>> search) {
        BitSet onBest = segmentsOf(best);
        for (double penalty : PENALTIES) {
            Optional<Path> candidate = search.apply(new PenaltyWeighting(weighting, onBest, penalty));
            // a stronger penalty only finds dearer routes, so one that is already too dear ends the search
            if (candidate.isEmpty() || candidate.get().cost() > best.cost() * MAX_STRETCH) {
                return Optional.empty();
            }
            if (sharedMeters(candidate.get(), onBest) <= best.distanceMeters() * MAX_SHARED) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    /** How much of the path runs along the given segments, in either direction. */
    static double sharedMeters(Path path, BitSet segments) {
        double shared = 0;
        for (int piece = 0; piece < path.pieceCount(); piece++) {
            if (segments.get(RoadGraph.segmentOf(path.pieceEntry(piece)))) {
                shared += path.pieceMeters(piece);
            }
        }
        return shared;
    }

    static BitSet segmentsOf(Path path) {
        BitSet segments = new BitSet();
        for (int piece = 0; piece < path.pieceCount(); piece++) {
            segments.set(RoadGraph.segmentOf(path.pieceEntry(piece)));
        }
        return segments;
    }
}
