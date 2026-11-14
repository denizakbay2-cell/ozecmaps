package com.citynavigator.tools;

import com.citynavigator.osm.OplReader;
import com.citynavigator.osm.RoadTags;
import com.citynavigator.routing.NationalGraph;
import com.citynavigator.routing.NationalGraphFile;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Turns the car roads of a country (OPL text from {@code osmium}) into a {@link NationalGraph} file:
 * <pre>
 *   java -Xmx3g -cp target/classes com.citynavigator.tools.BuildNationalGraph \
 *        data/turkey-car.opl data/turkey-car.graph 2026-11-13T21:22:20Z
 * </pre>
 * See {@code scripts/build-national-graph.sh} for the whole pipeline.
 */
public final class BuildNationalGraph {

    private static final Logger LOGGER = Logger.getLogger(BuildNationalGraph.class.getName());

    private BuildNationalGraph() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("usage: BuildNationalGraph <roads.opl> <output.graph> [data timestamp]");
        }
        NationalGraph graph = build(Path.of(args[0]), args.length > 2 ? Instant.parse(args[2].strip()) : null);
        NationalGraphFile.write(graph, Path.of(args[1]));
        LOGGER.info(() -> String.format("%,d junctions, %,d edges, %,d shape points, %,d ways -> %s",
                graph.nodeCount(), graph.edgeCount(), graph.pillarCount(), graph.wayCount(), args[1]));
    }

    static NationalGraph build(Path opl, Instant dataTimestamp) throws IOException {
        NationalGraph.Builder builder = NationalGraph.builder().dataTimestamp(dataTimestamp);
        try (BufferedReader reader = Files.newBufferedReader(opl, StandardCharsets.UTF_8)) {
            OplReader.read(reader, new OplReader.Handler() {
                @Override
                public void node(long id, double lat, double lon) {
                    builder.addNode(id, lat, lon);
                }

                @Override
                public void way(long id, Map<String, String> tags, long[] nodes) {
                    if (!RoadTags.isParkingLane(tags)) {
                        RoadTags.interpret(id, tags).ifPresent(way -> builder.addWay(way, nodes));
                    }
                }
            });
        }
        return builder.build();
    }
}
