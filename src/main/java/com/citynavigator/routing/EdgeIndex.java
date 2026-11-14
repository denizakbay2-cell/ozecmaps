package com.citynavigator.routing;

import java.util.BitSet;
import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Finds the nearest road of a {@link NationalGraph} and the roads inside a map view. Every edge is filed into the
 * grid cells (about 550 m) its shape passes through, long straight pieces included, so a search only looks at a
 * few cells around the point.
 */
public final class EdgeIndex {

    static final double CELL_DEGREES = 0.005;

    private final NationalGraph graph;
    private final double originLat;
    private final double originLon;
    private final double cellLat;
    private final double cellLon;
    private final int rows;
    private final int columns;
    private final int[] cellStart;
    private final int[] cellEdges;

    public EdgeIndex(NationalGraph graph) {
        this.graph = graph;
        double[] bounds = graph.bounds();
        originLat = bounds[0];
        originLon = bounds[1];
        cellLat = CELL_DEGREES;
        cellLon = CELL_DEGREES / Math.cos(Math.toRadians((bounds[0] + bounds[2]) / 2));
        rows = (int) ((bounds[2] - bounds[0]) / cellLat) + 1;
        columns = (int) ((bounds[3] - bounds[1]) / cellLon) + 1;
        int cells = Math.multiplyExact(rows, columns);
        cellStart = new int[cells + 1];
        for (int edge = 0; edge < graph.edgeCount(); edge++) {
            forEachCell(edge, cell -> cellStart[cell + 1]++);
        }
        for (int cell = 0; cell < cells; cell++) {
            cellStart[cell + 1] += cellStart[cell];
        }
        cellEdges = new int[cellStart[cells]];
        int[] next = cellStart.clone();
        for (int edge = 0; edge < graph.edgeCount(); edge++) {
            int current = edge;
            forEachCell(edge, cell -> cellEdges[next[cell]++] = current);
        }
    }

    /**
     * The closest point on any accepted edge within the distance.
     */
    public Optional<EdgeSnap> nearest(GeoPoint point, double maxDistanceMeters, IntPredicate acceptEdge) {
        int row = (int) Math.floor((point.lat() - originLat) / cellLat);
        int column = (int) Math.floor((point.lon() - originLon) / cellLon);
        double cellMeters = Math.min(cellLat * GeoMath.METERS_PER_DEGREE,
                cellLon * GeoMath.METERS_PER_DEGREE * Math.cos(Math.toRadians(point.lat())));
        int lastRing = (int) Math.ceil(maxDistanceMeters / cellMeters) + 1;
        Candidate best = null;
        for (int ring = 0; ring <= lastRing; ring++) {
            if (best != null && best.distance <= (ring - 1) * cellMeters) {
                break;
            }
            for (int r = Math.max(0, row - ring); r <= Math.min(rows - 1, row + ring); r++) {
                for (int c = Math.max(0, column - ring); c <= Math.min(columns - 1, column + ring); c++) {
                    if (Math.abs(r - row) != ring && Math.abs(c - column) != ring) {
                        continue;
                    }
                    int cell = r * columns + c;
                    for (int i = cellStart[cell]; i < cellStart[cell + 1]; i++) {
                        int edge = cellEdges[i];
                        if (acceptEdge.test(edge)) {
                            best = closer(best, closestOn(edge, point));
                        }
                    }
                }
            }
        }
        if (best == null || best.distance > maxDistanceMeters) {
            return Optional.empty();
        }
        return Optional.of(snapOf(best));
    }

    /**
     * Accepted edges near the rectangle, at most {@code max} of them: those filed in the grid cells it touches, so a
     * few edges just outside it may be included.
     */
    public int[] edgesWithin(double minLat, double minLon, double maxLat, double maxLon, IntPredicate acceptEdge,
                             int max) {
        int firstRow = Math.max(0, (int) Math.floor((minLat - originLat) / cellLat));
        int lastRow = Math.min(rows - 1, (int) Math.floor((maxLat - originLat) / cellLat));
        int firstColumn = Math.max(0, (int) Math.floor((minLon - originLon) / cellLon));
        int lastColumn = Math.min(columns - 1, (int) Math.floor((maxLon - originLon) / cellLon));
        BitSet seen = new BitSet();
        int[] found = new int[Math.clamp(max, 0, 1024)];
        int count = 0;
        for (int r = firstRow; r <= lastRow && count < max; r++) {
            for (int c = firstColumn; c <= lastColumn && count < max; c++) {
                int cell = r * columns + c;
                for (int i = cellStart[cell]; i < cellStart[cell + 1] && count < max; i++) {
                    int edge = cellEdges[i];
                    if (!seen.get(edge) && acceptEdge.test(edge)) {
                        seen.set(edge);
                        if (count == found.length) {
                            found = java.util.Arrays.copyOf(found, (int) Math.min(max, 2L * count));
                        }
                        found[count++] = edge;
                    }
                }
            }
        }
        return java.util.Arrays.copyOf(found, count);
    }

    private Candidate closestOn(int edge, GeoPoint point) {
        Candidate best = null;
        for (int piece = 0; piece + 1 < graph.pointCount(edge); piece++) {
            GeoMath.Projection projection = GeoMath.project(point.lat(), point.lon(),
                    graph.pointLat(edge, piece), graph.pointLon(edge, piece),
                    graph.pointLat(edge, piece + 1), graph.pointLon(edge, piece + 1));
            if (best == null || projection.distanceMeters() < best.distance) {
                best = new Candidate(edge, piece, projection.fraction(), projection.distanceMeters());
            }
        }
        return best;
    }

    private EdgeSnap snapOf(Candidate candidate) {
        int edge = candidate.edge;
        double offset = 0;
        for (int piece = 0; piece < candidate.piece; piece++) {
            offset += pieceLength(edge, piece);
        }
        offset += candidate.fraction * pieceLength(edge, candidate.piece);
        GeoPoint onRoad = GeoMath.interpolate(graph.pointLat(edge, candidate.piece), graph.pointLon(edge, candidate.piece),
                graph.pointLat(edge, candidate.piece + 1), graph.pointLon(edge, candidate.piece + 1),
                candidate.fraction);
        return new EdgeSnap(edge, candidate.piece, offset, onRoad, candidate.distance);
    }

    private double pieceLength(int edge, int piece) {
        return GeoMath.distance(graph.pointLat(edge, piece), graph.pointLon(edge, piece),
                graph.pointLat(edge, piece + 1), graph.pointLon(edge, piece + 1));
    }

    private static Candidate closer(Candidate a, Candidate b) {
        return a == null || (b != null && b.distance < a.distance) ? b : a;
    }

    /** Files the edge under every cell its pieces pass through, sampling each piece at half a cell. */
    private void forEachCell(int edge, IntConsumer action) {
        int last = -1;
        double lat1 = graph.pointLat(edge, 0);
        double lon1 = graph.pointLon(edge, 0);
        for (int i = 1; i < graph.pointCount(edge); i++) {
            double lat2 = graph.pointLat(edge, i);
            double lon2 = graph.pointLon(edge, i);
            int steps = 1 + (int) (Math.max(Math.abs(lat2 - lat1) / cellLat, Math.abs(lon2 - lon1) / cellLon) * 2);
            for (int s = i == 1 ? 0 : 1; s <= steps; s++) {
                double t = (double) s / steps;
                int cell = cellOf(lat1 + (lat2 - lat1) * t, lon1 + (lon2 - lon1) * t);
                if (cell != last) {
                    action.accept(cell);
                    last = cell;
                }
            }
            lat1 = lat2;
            lon1 = lon2;
        }
    }

    private int cellOf(double lat, double lon) {
        int row = Math.clamp((long) Math.floor((lat - originLat) / cellLat), 0, rows - 1);
        int column = Math.clamp((long) Math.floor((lon - originLon) / cellLon), 0, columns - 1);
        return row * columns + column;
    }

    private record Candidate(int edge, int piece, double fraction, double distance) {
    }
}
