package com.citynavigator.routing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The road network as an immutable graph held in primitive arrays, so a district of tens of thousands of nodes
 * takes a few megabytes and A* touches no boxed objects.
 *
 * <p>Every pair of consecutive way nodes forms a <em>segment</em>. A segment can be travelled forward (the way's
 * own direction) or backward; such a directed use is an <em>entry</em>, encoded as {@code segment << 1 | direction}.
 * Each node keeps the entries that leave it, whether or not a travel mode may use them; the weighting decides.
 */
public final class RoadGraph implements RoutingGraph {

    public static final int FORWARD = 0;
    public static final int BACKWARD = 1;

    private final double[] nodeLat;
    private final double[] nodeLon;
    private final RoadWay[] ways;
    private final int[] wayFirstSegment;
    private final Map<Long, Integer> wayIndexById;
    private final int[] segmentFrom;
    private final int[] segmentTo;
    private final int[] segmentWay;
    private final double[] segmentLength;
    private final int[] adjacencyStart;
    private final int[] adjacency;
    private final double[] bounds;

    private RoadGraph(double[] nodeLat, double[] nodeLon, List<RoadWay> ways, List<int[]> wayNodes,
                      Map<Long, Integer> wayIndexById) {
        this.nodeLat = nodeLat;
        this.nodeLon = nodeLon;
        this.ways = ways.toArray(RoadWay[]::new);
        this.wayIndexById = Map.copyOf(wayIndexById);

        wayFirstSegment = new int[this.ways.length + 1];
        for (int way = 0; way < this.ways.length; way++) {
            wayFirstSegment[way + 1] = wayFirstSegment[way] + wayNodes.get(way).length - 1;
        }
        int segments = wayFirstSegment[this.ways.length];
        segmentFrom = new int[segments];
        segmentTo = new int[segments];
        segmentWay = new int[segments];
        segmentLength = new double[segments];
        for (int way = 0; way < this.ways.length; way++) {
            int[] nodes = wayNodes.get(way);
            for (int i = 0; i + 1 < nodes.length; i++) {
                int segment = wayFirstSegment[way] + i;
                segmentFrom[segment] = nodes[i];
                segmentTo[segment] = nodes[i + 1];
                segmentWay[segment] = way;
                segmentLength[segment] = GeoMath.distance(nodeLat[nodes[i]], nodeLon[nodes[i]],
                        nodeLat[nodes[i + 1]], nodeLon[nodes[i + 1]]);
            }
        }

        // compressed adjacency lists: entries of node n are adjacency[adjacencyStart[n] .. adjacencyStart[n + 1])
        adjacencyStart = new int[nodeLat.length + 1];
        for (int segment = 0; segment < segments; segment++) {
            adjacencyStart[segmentFrom[segment] + 1]++;
            adjacencyStart[segmentTo[segment] + 1]++;
        }
        for (int node = 0; node < nodeLat.length; node++) {
            adjacencyStart[node + 1] += adjacencyStart[node];
        }
        adjacency = new int[2 * segments];
        int[] next = Arrays.copyOf(adjacencyStart, nodeLat.length);
        for (int segment = 0; segment < segments; segment++) {
            adjacency[next[segmentFrom[segment]]++] = entry(segment, FORWARD);
            adjacency[next[segmentTo[segment]]++] = entry(segment, BACKWARD);
        }
        bounds = boundsOf(nodeLat, nodeLon);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static int entry(int segment, int direction) {
        return segment << 1 | direction;
    }

    public static int segmentOf(int entry) {
        return entry >>> 1;
    }

    public static int directionOf(int entry) {
        return entry & 1;
    }

    /**
     * The same segment travelled the other way.
     */
    public static int reverse(int entry) {
        return entry ^ 1;
    }

    public int nodeCount() {
        return nodeLat.length;
    }

    public int segmentCount() {
        return segmentFrom.length;
    }

    public int wayCount() {
        return ways.length;
    }

    public double lat(int node) {
        return nodeLat[node];
    }

    public double lon(int node) {
        return nodeLon[node];
    }

    public GeoPoint point(int node) {
        return new GeoPoint(nodeLat[node], nodeLon[node]);
    }

    public int from(int segment) {
        return segmentFrom[segment];
    }

    public int to(int segment) {
        return segmentTo[segment];
    }

    public double length(int segment) {
        return segmentLength[segment];
    }

    public RoadWay way(int segment) {
        return ways[segmentWay[segment]];
    }

    public long wayId(int segment) {
        return way(segment).id();
    }

    /**
     * Position of the segment within its way: 0 for the segment that starts at the way's first node.
     */
    public int sequence(int segment) {
        return segment - wayFirstSegment[segmentWay[segment]];
    }

    /**
     * The segment that starts at the given node position of a way, or -1 if the way or position is unknown.
     */
    public int segment(long wayId, int sequence) {
        Integer way = wayIndexById.get(wayId);
        if (way == null || sequence < 0 || wayFirstSegment[way] + sequence >= wayFirstSegment[way + 1]) {
            return -1;
        }
        return wayFirstSegment[way] + sequence;
    }

    public int source(int entry) {
        int segment = segmentOf(entry);
        return directionOf(entry) == FORWARD ? segmentFrom[segment] : segmentTo[segment];
    }

    public int target(int entry) {
        int segment = segmentOf(entry);
        return directionOf(entry) == FORWARD ? segmentTo[segment] : segmentFrom[segment];
    }

    @Override
    public RoadWay roadOf(int entry) {
        return way(segmentOf(entry));
    }

    @Override
    public boolean allows(int entry, TravelMode mode) {
        return way(segmentOf(entry)).allows(mode, directionOf(entry));
    }

    /** A segment is straight, so it leaves its source towards its target. */
    @Override
    public double initialBearing(int entry) {
        int from = source(entry);
        int to = target(entry);
        return GeoMath.bearing(nodeLat[from], nodeLon[from], nodeLat[to], nodeLon[to]);
    }

    public boolean allowsAny(int segment, TravelMode mode) {
        return way(segment).allowsAny(mode);
    }

    @Override
    public int firstEntry(int node) {
        return adjacencyStart[node];
    }

    @Override
    public int endEntry(int node) {
        return adjacencyStart[node + 1];
    }

    @Override
    public int entryAt(int index) {
        return adjacency[index];
    }

    /**
     * {@code [minLat, minLon, maxLat, maxLon]} of all nodes.
     */
    public double[] bounds() {
        return bounds.clone();
    }

    /**
     * Whether the point lies inside the network's bounding box, widened by the margin.
     */
    public boolean covers(GeoPoint point, double marginMeters) {
        if (nodeLat.length == 0) {
            return false;
        }
        double latMargin = marginMeters / GeoMath.METERS_PER_DEGREE;
        double lonMargin = latMargin / Math.cos(Math.toRadians(point.lat()));
        return point.lat() >= bounds[0] - latMargin && point.lat() <= bounds[2] + latMargin
                && point.lon() >= bounds[1] - lonMargin && point.lon() <= bounds[3] + lonMargin;
    }

    private static double[] boundsOf(double[] lat, double[] lon) {
        double[] box = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (int node = 0; node < lat.length; node++) {
            box[0] = Math.min(box[0], lat[node]);
            box[1] = Math.min(box[1], lon[node]);
            box[2] = Math.max(box[2], lat[node]);
            box[3] = Math.max(box[3], lon[node]);
        }
        return lat.length == 0 ? new double[4] : box;
    }

    /**
     * Collects nodes first, then ways that refer to them by their OpenStreetMap ids.
     */
    public static final class Builder {

        private final Map<Long, Integer> nodeIndex = new HashMap<>();
        private final List<RoadWay> ways = new ArrayList<>();
        private final List<int[]> wayNodes = new ArrayList<>();
        private final Map<Long, Integer> wayIndex = new HashMap<>();
        private double[] lat = new double[1024];
        private double[] lon = new double[1024];
        private int nodes;

        private Builder() {
        }

        public Builder addNode(long id, double latitude, double longitude) {
            if (nodeIndex.putIfAbsent(id, nodes) != null) {
                throw new IllegalArgumentException("node " + id + " was added twice");
            }
            if (nodes == lat.length) {
                lat = Arrays.copyOf(lat, nodes * 2);
                lon = Arrays.copyOf(lon, nodes * 2);
            }
            lat[nodes] = latitude;
            lon[nodes] = longitude;
            nodes++;
            return this;
        }

        public Builder addWay(RoadWay way, long[] nodeIds) {
            if (nodeIds.length < 2) {
                throw new IllegalArgumentException("way " + way.id() + " needs at least two nodes");
            }
            if (wayIndex.containsKey(way.id())) {
                throw new IllegalArgumentException("way " + way.id() + " was added twice");
            }
            int[] indexes = new int[nodeIds.length];
            for (int i = 0; i < nodeIds.length; i++) {
                Integer index = nodeIndex.get(nodeIds[i]);
                if (index == null) {
                    throw new IllegalArgumentException("way " + way.id() + " refers to unknown node " + nodeIds[i]);
                }
                if (i > 0 && index == indexes[i - 1]) {
                    throw new IllegalArgumentException("way " + way.id() + " repeats node " + nodeIds[i]);
                }
                indexes[i] = index;
            }
            wayIndex.put(way.id(), ways.size());
            ways.add(way);
            wayNodes.add(indexes);
            return this;
        }

        public RoadGraph build() {
            return new RoadGraph(Arrays.copyOf(lat, nodes), Arrays.copyOf(lon, nodes), ways, wayNodes, wayIndex);
        }
    }
}
