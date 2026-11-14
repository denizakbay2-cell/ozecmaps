package com.citynavigator.testsupport;

import com.citynavigator.osm.RoadExtract;
import com.citynavigator.routing.RoadGraph;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.zip.GZIPInputStream;

/**
 * The bundled Kadıköy extract as a routing graph, read once per test run (no database involved).
 */
public final class KadikoyNetwork {

    private static RoadGraph graph;

    private KadikoyNetwork() {
    }

    public static synchronized RoadGraph graph() {
        if (graph == null) {
            graph = load();
        }
        return graph;
    }

    private static RoadGraph load() {
        try (InputStream json = new GZIPInputStream(
                KadikoyNetwork.class.getClassLoader().getResourceAsStream("osm/roads.json.gz"))) {
            RoadExtract extract = RoadExtract.read(json);
            RoadGraph.Builder builder = RoadGraph.builder();
            extract.nodes().forEach(node -> builder.addNode(node.id(), node.lat(), node.lon()));
            extract.ways().forEach(way -> builder.addWay(way.way(),
                    way.nodes().stream().mapToLong(Long::longValue).toArray()));
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
