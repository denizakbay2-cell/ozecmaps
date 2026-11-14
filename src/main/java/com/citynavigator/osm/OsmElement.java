package com.citynavigator.osm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A node, way or relation as delivered by the Overpass API.
 *
 * @param lat   latitude of a node, or of the centre of a way/relation exported with {@code out center};
 *              {@link Double#NaN} if the element has no position
 * @param nodes node ids of a way, in order; empty for other elements
 */
public record OsmElement(String type, long id, double lat, double lon, List<Long> nodes, Map<String, String> tags) {

    public OsmElement {
        nodes = List.copyOf(nodes);
        tags = Map.copyOf(tags);
    }

    public boolean hasPosition() {
        return !Double.isNaN(lat) && !Double.isNaN(lon);
    }

    /**
     * The way's nodes without immediate repetitions, which OpenStreetMap data occasionally contains and which would
     * make zero-length segments.
     */
    public List<Long> distinctConsecutiveNodes() {
        List<Long> distinct = new ArrayList<>(nodes.size());
        for (Long node : nodes) {
            if (distinct.isEmpty() || !distinct.getLast().equals(node)) {
                distinct.add(node);
            }
        }
        return distinct;
    }
}
