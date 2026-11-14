package com.citynavigator.osm;

import com.citynavigator.routing.RoadWay;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The routable part of a road extract: interpreted ways and exactly the nodes they use.
 */
public final class RoadExtract {

    private final List<Node> nodes;
    private final List<Way> ways;
    private final Instant timestamp;
    private final int skippedWays;

    private RoadExtract(List<Node> nodes, List<Way> ways, Instant timestamp, int skippedWays) {
        this.nodes = List.copyOf(nodes);
        this.ways = List.copyOf(ways);
        this.timestamp = timestamp;
        this.skippedWays = skippedWays;
    }

    /**
     * Reads an Overpass API document with road ways ({@code out body}) and their nodes ({@code out skel}).
     */
    public static RoadExtract read(InputStream json) {
        Map<Long, double[]> positions = new HashMap<>();
        List<Way> candidates = new ArrayList<>();
        Instant timestamp = OverpassReader.read(json, element -> {
            switch (element.type()) {
                case "node" -> positions.put(element.id(), new double[] {element.lat(), element.lon()});
                case "way" -> RoadTags.interpret(element.id(), element.tags()).ifPresent(way -> {
                    List<Long> wayNodes = element.distinctConsecutiveNodes();
                    if (wayNodes.size() >= 2) {
                        candidates.add(new Way(way, wayNodes));
                    }
                });
                default -> {
                    // relations carry nothing the router needs
                }
            }
        }).orElse(null);

        List<Way> ways = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (Way way : candidates) {
            if (positions.keySet().containsAll(way.nodes())) {
                ways.add(way);
                used.addAll(way.nodes());
            }
        }
        List<Node> nodes = new ArrayList<>(used.size());
        for (Long id : used) {
            double[] position = positions.get(id);
            nodes.add(new Node(id, position[0], position[1]));
        }
        nodes.sort(Comparator.comparingLong(Node::id));
        ways.sort(Comparator.comparingLong(way -> way.way().id()));
        return new RoadExtract(nodes, ways, timestamp, candidates.size() - ways.size());
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<Way> ways() {
        return ways;
    }

    public Optional<Instant> timestamp() {
        return Optional.ofNullable(timestamp);
    }

    /**
     * Ways dropped because the extract lacked some of their nodes.
     */
    public int skippedWays() {
        return skippedWays;
    }

    public record Node(long id, double lat, double lon) {
    }

    public record Way(RoadWay way, List<Long> nodes) {

        public Way {
            nodes = List.copyOf(nodes);
        }
    }
}
