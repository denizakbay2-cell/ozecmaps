package com.citynavigator.routing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The car road network of a whole country, compact enough for one server.
 *
 * <p>Unlike {@link RoadGraph}, which keeps every OpenStreetMap node, this graph keeps only the nodes where roads meet
 * or end ("towers"); the nodes in between ("pillars") are only the shape of an edge. That cuts the nodes a search
 * has to visit several times over. Coordinates are stored as integers of 1e-7 degrees (about a centimetre).
 * Entries are encoded as in {@link RoadGraph}: {@code edge << 1 | direction}.
 */
public final class NationalGraph implements RoutingGraph {

    public static final int CAR_FORWARD = 1;
    public static final int CAR_BACKWARD = 2;
    public static final int ROUNDABOUT = 4;
    static final double E7 = 1e7;

    private final Parts parts;
    private final int[] adjacencyStart;
    private final int[] adjacency;
    private final double[] bounds;

    NationalGraph(Parts parts) {
        this.parts = parts;
        int towers = parts.towerLat.length;
        int edges = parts.edgeFrom.length;
        adjacencyStart = new int[towers + 1];
        for (int edge = 0; edge < edges; edge++) {
            adjacencyStart[parts.edgeFrom[edge] + 1]++;
            adjacencyStart[parts.edgeTo[edge] + 1]++;
        }
        for (int tower = 0; tower < towers; tower++) {
            adjacencyStart[tower + 1] += adjacencyStart[tower];
        }
        adjacency = new int[2 * edges];
        int[] next = Arrays.copyOf(adjacencyStart, towers);
        for (int edge = 0; edge < edges; edge++) {
            adjacency[next[parts.edgeFrom[edge]]++] = RoadGraph.entry(edge, RoadGraph.FORWARD);
            adjacency[next[parts.edgeTo[edge]]++] = RoadGraph.entry(edge, RoadGraph.BACKWARD);
        }
        bounds = boundsOf(parts.towerLat, parts.towerLon);
    }

    public int nodeCount() {
        return parts.towerLat.length;
    }

    public int edgeCount() {
        return parts.edgeFrom.length;
    }

    public int wayCount() {
        return parts.wayOsmId.length;
    }

    public int pillarCount() {
        return parts.pillarLat.length;
    }

    /** When the OpenStreetMap data was taken, if known. */
    public Instant dataTimestamp() {
        return parts.dataTimestamp;
    }

    public double lat(int node) {
        return parts.towerLat[node] / E7;
    }

    public double lon(int node) {
        return parts.towerLon[node] / E7;
    }

    public int from(int edge) {
        return parts.edgeFrom[edge];
    }

    public int to(int edge) {
        return parts.edgeTo[edge];
    }

    public double length(int edge) {
        return parts.edgeLength[edge];
    }

    public int source(int entry) {
        int edge = RoadGraph.segmentOf(entry);
        return RoadGraph.directionOf(entry) == RoadGraph.FORWARD ? parts.edgeFrom[edge] : parts.edgeTo[edge];
    }

    public int target(int entry) {
        int edge = RoadGraph.segmentOf(entry);
        return RoadGraph.directionOf(entry) == RoadGraph.FORWARD ? parts.edgeTo[edge] : parts.edgeFrom[edge];
    }

    /**
     * Points of the edge's shape in the forward direction: its first tower, the pillars, its second tower.
     */
    public int pointCount(int edge) {
        return parts.edgePillarStart[edge + 1] - parts.edgePillarStart[edge] + 2;
    }

    public double pointLat(int edge, int index) {
        if (index == 0) {
            return lat(parts.edgeFrom[edge]);
        }
        if (index == pointCount(edge) - 1) {
            return lat(parts.edgeTo[edge]);
        }
        return parts.pillarLat[parts.edgePillarStart[edge] + index - 1] / E7;
    }

    public double pointLon(int edge, int index) {
        if (index == 0) {
            return lon(parts.edgeFrom[edge]);
        }
        if (index == pointCount(edge) - 1) {
            return lon(parts.edgeTo[edge]);
        }
        return parts.pillarLon[parts.edgePillarStart[edge] + index - 1] / E7;
    }

    public GeoPoint point(int edge, int index) {
        return new GeoPoint(pointLat(edge, index), pointLon(edge, index));
    }

    public RoadClass roadClass(int edge) {
        return RoadClass.values()[parts.wayClass[parts.edgeWay[edge]]];
    }

    public String name(int edge) {
        int name = parts.wayName[parts.edgeWay[edge]];
        return name < 0 ? null : parts.names[name];
    }

    public long wayId(int edge) {
        return parts.wayOsmId[parts.edgeWay[edge]];
    }

    /**
     * Free-flow car speed: the class speed, lowered by a signposted limit.
     */
    public double carSpeedKmh(int edge) {
        int way = parts.edgeWay[edge];
        int speed = RoadClass.values()[parts.wayClass[way]].nationalCarKmh();
        if (speed == 0) {
            speed = TravelMode.CAR.fallbackSpeedKmh();
        }
        int limit = parts.wayMaxSpeed[way];
        return limit > 0 ? Math.min(speed, limit) : speed;
    }

    @Override
    public RoadWay roadOf(int entry) {
        int edge = RoadGraph.segmentOf(entry);
        int way = parts.edgeWay[edge];
        int flags = parts.wayFlags[way];
        return new RoadWay(parts.wayOsmId[way], name(edge), RoadClass.values()[parts.wayClass[way]],
                parts.wayMaxSpeed[way], (flags & ROUNDABOUT) != 0, TravelMode.CAR.accessBits(
                (flags & CAR_FORWARD) != 0, (flags & CAR_BACKWARD) != 0));
    }

    /** The national graph is for cars only. */
    @Override
    public boolean allows(int entry, TravelMode mode) {
        if (mode != TravelMode.CAR) {
            return false;
        }
        int flags = parts.wayFlags[parts.edgeWay[RoadGraph.segmentOf(entry)]];
        return (flags & (RoadGraph.directionOf(entry) == RoadGraph.FORWARD ? CAR_FORWARD : CAR_BACKWARD)) != 0;
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

    @Override
    public double initialBearing(int entry) {
        int edge = RoadGraph.segmentOf(entry);
        int last = pointCount(edge) - 1;
        if (RoadGraph.directionOf(entry) == RoadGraph.FORWARD) {
            return GeoMath.bearing(pointLat(edge, 0), pointLon(edge, 0), pointLat(edge, 1), pointLon(edge, 1));
        }
        return GeoMath.bearing(pointLat(edge, last), pointLon(edge, last), pointLat(edge, last - 1),
                pointLon(edge, last - 1));
    }

    /** {@code [minLat, minLon, maxLat, maxLon]} of all towers. */
    public double[] bounds() {
        return bounds.clone();
    }

    public boolean covers(GeoPoint point, double marginMeters) {
        if (nodeCount() == 0) {
            return false;
        }
        double latMargin = marginMeters / GeoMath.METERS_PER_DEGREE;
        double lonMargin = latMargin / Math.cos(Math.toRadians(point.lat()));
        return point.lat() >= bounds[0] - latMargin && point.lat() <= bounds[2] + latMargin
                && point.lon() >= bounds[1] - lonMargin && point.lon() <= bounds[3] + lonMargin;
    }

    Parts parts() {
        return parts;
    }

    private static double[] boundsOf(int[] lat, int[] lon) {
        if (lat.length == 0) {
            return new double[4];
        }
        int minLat = Integer.MAX_VALUE;
        int minLon = Integer.MAX_VALUE;
        int maxLat = Integer.MIN_VALUE;
        int maxLon = Integer.MIN_VALUE;
        for (int i = 0; i < lat.length; i++) {
            minLat = Math.min(minLat, lat[i]);
            minLon = Math.min(minLon, lon[i]);
            maxLat = Math.max(maxLat, lat[i]);
            maxLon = Math.max(maxLon, lon[i]);
        }
        return new double[] {minLat / E7, minLon / E7, maxLat / E7, maxLon / E7};
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Collects the nodes (first) and the car ways of a country, then cuts the ways into edges between towers. Holds
     * everything in primitive arrays: a country has tens of millions of nodes.
     */
    public static final class Builder {

        private long[] nodeIds = new long[1 << 16];
        private int[] nodeLat = new int[1 << 16];
        private int[] nodeLon = new int[1 << 16];
        private int nodes;
        private boolean sorted = true;
        private boolean nodesComplete;
        private long[] wayIds = new long[1 << 12];
        private byte[] wayClass = new byte[1 << 12];
        private byte[] wayFlags = new byte[1 << 12];
        private short[] wayMaxSpeed = new short[1 << 12];
        private int[] wayName = new int[1 << 12];
        private int[] wayRefStart = new int[(1 << 12) + 1];
        private int ways;
        private int[] refs = new int[1 << 16];
        private int refCount;
        private final Map<String, Integer> nameIndex = new HashMap<>();
        private final List<String> names = new ArrayList<>();
        private Instant dataTimestamp;

        private Builder() {
        }

        public Builder addNode(long id, double lat, double lon) {
            if (nodesComplete) {
                throw new IllegalStateException("all nodes must be added before the first way");
            }
            if (nodes == nodeIds.length) {
                nodeIds = Arrays.copyOf(nodeIds, nodes * 2);
                nodeLat = Arrays.copyOf(nodeLat, nodes * 2);
                nodeLon = Arrays.copyOf(nodeLon, nodes * 2);
            }
            if (nodes > 0 && id <= nodeIds[nodes - 1]) {
                sorted = false;
            }
            nodeIds[nodes] = id;
            nodeLat[nodes] = (int) Math.round(lat * E7);
            nodeLon[nodes] = (int) Math.round(lon * E7);
            nodes++;
            return this;
        }

        /**
         * Adds a way if cars may use it and all its nodes are known.
         *
         * @return whether the way was taken
         */
        public boolean addWay(RoadWay way, long[] nodeRefs) {
            if (!nodesComplete) {
                completeNodes();
            }
            if (!way.allowsAny(TravelMode.CAR)) {
                return false;
            }
            int start = refCount;
            int previous = -1;
            for (long ref : nodeRefs) {
                int index = Arrays.binarySearch(nodeIds, 0, nodes, ref);
                if (index < 0) {
                    refCount = start;
                    return false;
                }
                if (index != previous) {
                    if (refCount == refs.length) {
                        refs = Arrays.copyOf(refs, refCount * 2);
                    }
                    refs[refCount++] = index;
                    previous = index;
                }
            }
            if (refCount - start < 2) {
                refCount = start;
                return false;
            }
            if (ways + 1 == wayIds.length) {
                int size = wayIds.length * 2;
                wayIds = Arrays.copyOf(wayIds, size);
                wayClass = Arrays.copyOf(wayClass, size);
                wayFlags = Arrays.copyOf(wayFlags, size);
                wayMaxSpeed = Arrays.copyOf(wayMaxSpeed, size);
                wayName = Arrays.copyOf(wayName, size);
                wayRefStart = Arrays.copyOf(wayRefStart, size + 1);
            }
            wayIds[ways] = way.id();
            wayClass[ways] = (byte) way.roadClass().ordinal();
            wayFlags[ways] = (byte) ((way.allows(TravelMode.CAR, RoadGraph.FORWARD) ? CAR_FORWARD : 0)
                    | (way.allows(TravelMode.CAR, RoadGraph.BACKWARD) ? CAR_BACKWARD : 0)
                    | (way.roundabout() ? ROUNDABOUT : 0));
            wayMaxSpeed[ways] = (short) Math.min(way.maxSpeedKmh(), Short.MAX_VALUE);
            wayName[ways] = way.hasName() ? nameIndex.computeIfAbsent(way.name(), name -> {
                names.add(name);
                return names.size() - 1;
            }) : -1;
            wayRefStart[ways] = start;
            ways++;
            wayRefStart[ways] = refCount;
            return true;
        }

        public Builder dataTimestamp(Instant timestamp) {
            this.dataTimestamp = timestamp;
            return this;
        }

        public NationalGraph build() {
            if (!nodesComplete) {
                completeNodes();
            }
            // a tower is a node where ways meet, a way ends, or a way crosses itself
            byte[] uses = new byte[nodes];
            for (int way = 0; way < ways; way++) {
                int first = wayRefStart[way];
                int last = wayRefStart[way + 1] - 1;
                for (int i = first; i <= last; i++) {
                    int bonus = i == first || i == last ? 2 : 1;
                    uses[refs[i]] = (byte) Math.min(2, uses[refs[i]] + bonus);
                }
                if (refs[first] == refs[last] && last - first >= 2) {
                    // a closed way (a roundabout, a loop road) needs a second tower, or it would be a single edge from
                    // its first node back to itself
                    uses[refs[(first + last) / 2]] = 2;
                }
            }
            int[] towerOf = new int[nodes];
            int towers = 0;
            for (int node = 0; node < nodes; node++) {
                towerOf[node] = uses[node] >= 2 ? towers++ : -1;
            }
            int[] counts = countEdgesAndPillars(towerOf);
            Parts parts = new Parts();
            parts.dataTimestamp = dataTimestamp;
            parts.towerLat = new int[towers];
            parts.towerLon = new int[towers];
            for (int node = 0; node < nodes; node++) {
                if (towerOf[node] >= 0) {
                    parts.towerLat[towerOf[node]] = nodeLat[node];
                    parts.towerLon[towerOf[node]] = nodeLon[node];
                }
            }
            fillEdges(parts, towerOf, counts[0], counts[1]);
            parts.wayOsmId = Arrays.copyOf(wayIds, ways);
            parts.wayClass = Arrays.copyOf(wayClass, ways);
            parts.wayFlags = Arrays.copyOf(wayFlags, ways);
            parts.wayMaxSpeed = Arrays.copyOf(wayMaxSpeed, ways);
            parts.wayName = Arrays.copyOf(wayName, ways);
            parts.names = names.toArray(String[]::new);
            return new NationalGraph(parts);
        }

        /** {@code [edges, pillars]}; an edge that would start and end at the same tower is left out. */
        private int[] countEdgesAndPillars(int[] towerOf) {
            int edges = 0;
            int pillars = 0;
            for (int way = 0; way < ways; way++) {
                int from = wayRefStart[way];
                for (int i = from + 1; i < wayRefStart[way + 1]; i++) {
                    if (towerOf[refs[i]] >= 0) {
                        if (towerOf[refs[from]] != towerOf[refs[i]]) {
                            edges++;
                            pillars += i - from - 1;
                        }
                        from = i;
                    }
                }
            }
            return new int[] {edges, pillars};
        }

        private void fillEdges(Parts parts, int[] towerOf, int edges, int pillars) {
            parts.edgeFrom = new int[edges];
            parts.edgeTo = new int[edges];
            parts.edgeWay = new int[edges];
            parts.edgeLength = new float[edges];
            parts.edgePillarStart = new int[edges + 1];
            parts.pillarLat = new int[pillars];
            parts.pillarLon = new int[pillars];
            int edge = 0;
            int pillar = 0;
            for (int way = 0; way < ways; way++) {
                int from = wayRefStart[way];
                for (int i = from + 1; i < wayRefStart[way + 1]; i++) {
                    if (towerOf[refs[i]] < 0) {
                        continue;
                    }
                    if (towerOf[refs[from]] != towerOf[refs[i]]) {
                        double length = 0;
                        for (int k = from; k < i; k++) {
                            length += GeoMath.distance(nodeLat[refs[k]] / E7, nodeLon[refs[k]] / E7,
                                    nodeLat[refs[k + 1]] / E7, nodeLon[refs[k + 1]] / E7);
                        }
                        parts.edgeFrom[edge] = towerOf[refs[from]];
                        parts.edgeTo[edge] = towerOf[refs[i]];
                        parts.edgeWay[edge] = way;
                        parts.edgeLength[edge] = (float) length;
                        parts.edgePillarStart[edge] = pillar;
                        for (int k = from + 1; k < i; k++) {
                            parts.pillarLat[pillar] = nodeLat[refs[k]];
                            parts.pillarLon[pillar] = nodeLon[refs[k]];
                            pillar++;
                        }
                        edge++;
                    }
                    from = i;
                }
            }
            parts.edgePillarStart[edges] = pillar;
        }

        /** Nodes are looked up by id with binary search, so they must be in id order. */
        private void completeNodes() {
            nodesComplete = true;
            if (sorted) {
                return;
            }
            Integer[] order = new Integer[nodes];
            for (int i = 0; i < nodes; i++) {
                order[i] = i;
            }
            long[] ids = nodeIds;
            Arrays.sort(order, (a, b) -> Long.compare(ids[a], ids[b]));
            long[] sortedIds = new long[nodes];
            int[] sortedLat = new int[nodes];
            int[] sortedLon = new int[nodes];
            for (int i = 0; i < nodes; i++) {
                sortedIds[i] = nodeIds[order[i]];
                sortedLat[i] = nodeLat[order[i]];
                sortedLon[i] = nodeLon[order[i]];
            }
            nodeIds = sortedIds;
            nodeLat = sortedLat;
            nodeLon = sortedLon;
        }
    }

    /**
     * The arrays a graph is made of, as the builder produces them and the graph file stores them.
     */
    static final class Parts {

        int[] towerLat;
        int[] towerLon;
        int[] edgeFrom;
        int[] edgeTo;
        int[] edgeWay;
        float[] edgeLength;
        /** Pillars of edge e are {@code pillar[edgePillarStart[e] .. edgePillarStart[e + 1])}; one longer than edges. */
        int[] edgePillarStart;
        int[] pillarLat;
        int[] pillarLon;
        long[] wayOsmId;
        byte[] wayClass;
        byte[] wayFlags;
        short[] wayMaxSpeed;
        int[] wayName;
        String[] names;
        Instant dataTimestamp;
    }
}
