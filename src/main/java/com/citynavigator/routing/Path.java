package com.citynavigator.routing;

import java.util.List;

/**
 * A computed route: a polyline from the start to the destination and, for every piece between two consecutive
 * points, the segment entry it runs along with its length and travel time.
 */
public final class Path {

    private final List<GeoPoint> points;
    private final int[] pointNodes;
    private final int[] pieceEntries;
    private final double[] pieceMeters;
    private final double[] pieceSeconds;
    private final double[] pieceFreeFlowSeconds;
    private final double[] metersBefore;
    private final double[] secondsBefore;
    private final double cost;
    private final int visitedNodes;

    Path(List<GeoPoint> points, int[] pointNodes, int[] pieceEntries, double[] pieceMeters, double[] pieceSeconds,
         double[] pieceFreeFlowSeconds, double cost, int visitedNodes) {
        this.points = List.copyOf(points);
        this.pointNodes = pointNodes;
        this.pieceEntries = pieceEntries;
        this.pieceMeters = pieceMeters;
        this.pieceSeconds = pieceSeconds;
        this.pieceFreeFlowSeconds = pieceFreeFlowSeconds;
        this.cost = cost;
        this.visitedNodes = visitedNodes;
        metersBefore = new double[points.size()];
        secondsBefore = new double[points.size()];
        for (int piece = 0; piece < pieceEntries.length; piece++) {
            metersBefore[piece + 1] = metersBefore[piece] + pieceMeters[piece];
            secondsBefore[piece + 1] = secondsBefore[piece] + pieceSeconds[piece];
        }
    }

    public List<GeoPoint> points() {
        return points;
    }

    public int pointCount() {
        return points.size();
    }

    public GeoPoint point(int index) {
        return points.get(index);
    }

    /**
     * The graph node at a point, or -1 for a start or destination that lies between two nodes.
     */
    public int pointNode(int index) {
        return pointNodes[index];
    }

    public int pieceCount() {
        return pieceEntries.length;
    }

    /**
     * The segment entry of the piece from point {@code piece} to point {@code piece + 1}.
     */
    public int pieceEntry(int piece) {
        return pieceEntries[piece];
    }

    public double pieceMeters(int piece) {
        return pieceMeters[piece];
    }

    public double pieceSeconds(int piece) {
        return pieceSeconds[piece];
    }

    /**
     * Travel time of the piece without congestion or incidents; the share of it in {@link #pieceSeconds} is how fast
     * traffic lets the piece be driven.
     */
    public double pieceFreeFlowSeconds(int piece) {
        return pieceFreeFlowSeconds[piece];
    }

    public double metersBetween(int fromPoint, int toPoint) {
        return metersBefore[toPoint] - metersBefore[fromPoint];
    }

    public double secondsBetween(int fromPoint, int toPoint) {
        return secondsBefore[toPoint] - secondsBefore[fromPoint];
    }

    public double distanceMeters() {
        return metersBefore[points.size() - 1];
    }

    public double durationSeconds() {
        return secondsBefore[points.size() - 1];
    }

    /**
     * What the weighting minimised: seconds for the fastest route, metres for the shortest.
     */
    public double cost() {
        return cost;
    }

    /**
     * The travel time the same route would take without congestion.
     */
    public double freeFlowSeconds() {
        double total = 0;
        for (double seconds : pieceFreeFlowSeconds) {
            total += seconds;
        }
        return total;
    }

    /**
     * How many nodes the search settled; shows how much A*'s estimate saves compared to Dijkstra.
     */
    public int visitedNodes() {
        return visitedNodes;
    }
}
