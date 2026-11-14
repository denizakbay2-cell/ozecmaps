package com.citynavigator.routing;

import java.util.Optional;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Finds the road nearest to a coordinate. Segments are filed into a grid of roughly 220 m cells; a search looks
 * at rings of cells around the point and stops once no unvisited cell can hold anything closer.
 */
public final class SpatialIndex {

    static final double CELL_DEGREES = 0.002;

    private final RoadGraph graph;
    private final double originLat;
    private final double originLon;
    private final double cellLat;
    private final double cellLon;
    private final int rows;
    private final int columns;
    private final int[] cellStart;
    private final int[] cellSegments;

    public SpatialIndex(RoadGraph graph) {
        this.graph = graph;
        double[] bounds = graph.bounds();
        originLat = bounds[0];
        originLon = bounds[1];
        cellLat = CELL_DEGREES;
        cellLon = CELL_DEGREES / Math.cos(Math.toRadians((bounds[0] + bounds[2]) / 2));
        rows = (int) ((bounds[2] - bounds[0]) / cellLat) + 1;
        columns = (int) ((bounds[3] - bounds[1]) / cellLon) + 1;

        cellStart = new int[rows * columns + 1];
        for (int segment = 0; segment < graph.segmentCount(); segment++) {
            forEachCell(segment, cell -> cellStart[cell + 1]++);
        }
        for (int cell = 0; cell < rows * columns; cell++) {
            cellStart[cell + 1] += cellStart[cell];
        }
        cellSegments = new int[cellStart[rows * columns]];
        int[] next = cellStart.clone();
        for (int segment = 0; segment < graph.segmentCount(); segment++) {
            int current = segment;
            forEachCell(segment, cell -> cellSegments[next[cell]++] = current);
        }
    }

    /**
     * The closest point on any accepted segment within the distance, if there is one.
     */
    public Optional<Snap> nearest(GeoPoint point, double maxDistanceMeters, IntPredicate acceptSegment) {
        int row = row(point.lat());
        int column = column(point.lon());
        double cellMeters = Math.min(cellLat * GeoMath.METERS_PER_DEGREE,
                cellLon * GeoMath.METERS_PER_DEGREE * Math.cos(Math.toRadians(point.lat())));
        int lastRing = (int) Math.ceil(maxDistanceMeters / cellMeters) + 1;

        int bestSegment = -1;
        GeoMath.Projection best = null;
        for (int ring = 0; ring <= lastRing; ring++) {
            if (best != null && best.distanceMeters() <= (ring - 1) * cellMeters) {
                break;
            }
            for (int r = Math.max(0, row - ring); r <= Math.min(rows - 1, row + ring); r++) {
                for (int c = Math.max(0, column - ring); c <= Math.min(columns - 1, column + ring); c++) {
                    if (Math.abs(r - row) != ring && Math.abs(c - column) != ring) {
                        continue;
                    }
                    int cell = r * columns + c;
                    for (int i = cellStart[cell]; i < cellStart[cell + 1]; i++) {
                        int segment = cellSegments[i];
                        if (!acceptSegment.test(segment)) {
                            continue;
                        }
                        GeoMath.Projection projection = project(point, segment);
                        if (best == null || projection.distanceMeters() < best.distanceMeters()) {
                            best = projection;
                            bestSegment = segment;
                        }
                    }
                }
            }
        }
        if (best == null || best.distanceMeters() > maxDistanceMeters) {
            return Optional.empty();
        }
        int a = graph.from(bestSegment);
        int b = graph.to(bestSegment);
        GeoPoint onRoad = GeoMath.interpolate(graph.lat(a), graph.lon(a), graph.lat(b), graph.lon(b), best.fraction());
        return Optional.of(new Snap(bestSegment, best.fraction(), onRoad, best.distanceMeters()));
    }

    private GeoMath.Projection project(GeoPoint point, int segment) {
        int a = graph.from(segment);
        int b = graph.to(segment);
        return GeoMath.project(point.lat(), point.lon(), graph.lat(a), graph.lon(a), graph.lat(b), graph.lon(b));
    }

    private void forEachCell(int segment, IntConsumer action) {
        int a = graph.from(segment);
        int b = graph.to(segment);
        int firstRow = row(Math.min(graph.lat(a), graph.lat(b)));
        int lastRow = row(Math.max(graph.lat(a), graph.lat(b)));
        int firstColumn = column(Math.min(graph.lon(a), graph.lon(b)));
        int lastColumn = column(Math.max(graph.lon(a), graph.lon(b)));
        for (int r = firstRow; r <= lastRow; r++) {
            for (int c = firstColumn; c <= lastColumn; c++) {
                action.accept(r * columns + c);
            }
        }
    }

    private int row(double lat) {
        return (int) Math.floor((lat - originLat) / cellLat);
    }

    private int column(double lon) {
        return (int) Math.floor((lon - originLon) / cellLon);
    }
}
