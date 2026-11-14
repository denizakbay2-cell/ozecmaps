package com.citynavigator.routing;

import static com.citynavigator.testsupport.GraphFixture.ALL;
import static com.citynavigator.testsupport.GraphFixture.FOOT_ONLY;
import static com.citynavigator.testsupport.GraphFixture.ONE_WAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.citynavigator.testsupport.GraphFixture;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NationalGraphTest {

    private static final Instant TAKEN = Instant.parse("2026-11-13T21:22:20Z");

    @TempDir
    java.nio.file.Path folder;

    /**
     * <pre>
     *   1 - 2 - 3 - 4   Ana Cadde, primary, limit 50; 2 and 3 only shape it
     *               |
     *               5   Tek Yön, one-way north to south; continues as a loop 5 - 6 - 7 - 8 - 5
     *   9 - 10          a footpath, not for cars
     * </pre>
     */
    private static NationalGraph.Builder townBuilder() {
        NationalGraph.Builder builder = NationalGraph.builder()
                .addNode(1, lat(0), lon(0)).addNode(2, lat(0), lon(1)).addNode(3, lat(0.2), lon(2))
                .addNode(4, lat(0), lon(3)).addNode(5, lat(-1), lon(3)).addNode(6, lat(-2), lon(3))
                .addNode(7, lat(-2), lon(4)).addNode(8, lat(-1), lon(4))
                .addNode(9, lat(3), lon(0)).addNode(10, lat(3), lon(1))
                .dataTimestamp(TAKEN);
        assertThat(builder.addWay(new RoadWay(100, "Ana Cadde", RoadClass.PRIMARY, 50, false, ALL),
                new long[] {1, 2, 2, 3, 4})).isTrue();
        assertThat(builder.addWay(new RoadWay(101, "Tek Yön", RoadClass.RESIDENTIAL, 0, false, ONE_WAY),
                new long[] {4, 5})).isTrue();
        assertThat(builder.addWay(new RoadWay(102, null, RoadClass.TERTIARY, 0, true, ONE_WAY),
                new long[] {5, 6, 7, 8, 5})).isTrue();
        assertThat(builder.addWay(new RoadWay(103, null, RoadClass.FOOTWAY, 0, false, FOOT_ONLY),
                new long[] {9, 10})).isFalse();
        assertThat(builder.addWay(new RoadWay(104, "Hayalet", RoadClass.RESIDENTIAL, 0, false, ALL),
                new long[] {9, 99})).isFalse();
        return builder;
    }

    @Test
    void cutsWaysIntoEdgesBetweenJunctionsAndKeepsTheirShape() {
        NationalGraph graph = townBuilder().build();

        // towers: 1 and 4 (ends of Ana Cadde), 5 (where the loop starts), 7 (the loop's second tower)
        assertThat(graph.nodeCount()).isEqualTo(4);
        assertThat(graph.edgeCount()).isEqualTo(4);
        assertThat(graph.wayCount()).isEqualTo(3);
        assertThat(graph.pillarCount()).isEqualTo(4);
        assertThat(graph.dataTimestamp()).isEqualTo(TAKEN);

        int main = edgeOfWay(graph, 100);
        assertThat(graph.pointCount(main)).isEqualTo(4);
        assertThat(graph.point(main, 2).lat()).isCloseTo(lat(0.2), within(1e-7));
        double shapeLength = GeoMath.distance(at(0, 0), at(0, 1)) + GeoMath.distance(at(0, 1), at(0.2, 2))
                + GeoMath.distance(at(0.2, 2), at(0, 3));
        assertThat(graph.length(main)).isCloseTo(shapeLength, within(0.01));
        assertThat(graph.name(main)).isEqualTo("Ana Cadde");
        assertThat(graph.roadClass(main)).isEqualTo(RoadClass.PRIMARY);
        assertThat(graph.wayId(main)).isEqualTo(100);
        assertThat(graph.carSpeedKmh(main)).isEqualTo(50);
        assertThat(graph.roadOf(RoadGraph.entry(main, RoadGraph.FORWARD)).maxSpeedKmh()).isEqualTo(50);
        assertThat(graph.initialBearing(RoadGraph.entry(main, RoadGraph.FORWARD))).isCloseTo(90, within(0.5));
        assertThat(graph.initialBearing(RoadGraph.entry(main, RoadGraph.BACKWARD))).isBetween(270.0, 300.0);
    }

    @Test
    void knowsWhichDirectionsCarsMayUse() {
        NationalGraph graph = townBuilder().build();
        int oneWay = edgeOfWay(graph, 101);

        assertThat(graph.allows(RoadGraph.entry(oneWay, RoadGraph.FORWARD), TravelMode.CAR)).isTrue();
        assertThat(graph.allows(RoadGraph.entry(oneWay, RoadGraph.BACKWARD), TravelMode.CAR)).isFalse();
        assertThat(graph.allows(RoadGraph.entry(oneWay, RoadGraph.FORWARD), TravelMode.FOOT)).isFalse();
        assertThat(graph.carSpeedKmh(oneWay)).isEqualTo(RoadClass.RESIDENTIAL.nationalCarKmh());
        assertThat(graph.name(oneWay)).isEqualTo("Tek Yön");
        int loop = edgeOfWay(graph, 102);
        assertThat(graph.name(loop)).isNull();
        assertThat(graph.roadOf(RoadGraph.entry(loop, RoadGraph.FORWARD)).roundabout()).isTrue();
    }

    @Test
    void aLoopRoadBecomesTwoEdgesInsteadOfDisappearing() {
        NationalGraph graph = townBuilder().build();

        long loopEdges = java.util.stream.IntStream.range(0, graph.edgeCount())
                .filter(edge -> graph.wayId(edge) == 102).count();
        assertThat(loopEdges).isEqualTo(2);
    }

    @Test
    void acceptsNodesInAnyOrderButNotAfterTheFirstWay() {
        NationalGraph.Builder builder = NationalGraph.builder()
                .addNode(30, lat(0), lon(2)).addNode(10, lat(0), lon(0)).addNode(20, lat(0), lon(1));
        builder.addWay(new RoadWay(1, "A", RoadClass.SECONDARY, 0, false, ALL), new long[] {10, 20, 30});

        NationalGraph graph = builder.build();
        assertThat(graph.nodeCount()).isEqualTo(2);
        assertThat(graph.pointCount(0)).isEqualTo(3);
        assertThatThrownBy(() -> builder.addNode(40, 41, 29)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void coversItsAreaWithAMargin() {
        NationalGraph graph = townBuilder().build();

        assertThat(graph.covers(at(0, 1), 0)).isTrue();
        assertThat(graph.covers(at(-5, 1), 100)).isFalse();
        assertThat(graph.covers(at(-5, 1), 600)).isTrue();
        assertThat(NationalGraph.builder().build().covers(at(0, 0), 1_000)).isFalse();
        assertThat(graph.bounds()[0]).isCloseTo(lat(-2), within(1e-7));
    }

    @Test
    void survivesAWriteAndReadThroughItsFile() throws IOException {
        NationalGraph graph = townBuilder().build();
        java.nio.file.Path file = folder.resolve("town.graph");

        NationalGraphFile.write(graph, file);
        NationalGraph read = NationalGraphFile.read(file);

        assertThat(NationalGraphFile.exists(file)).isTrue();
        assertThat(read.nodeCount()).isEqualTo(graph.nodeCount());
        assertThat(read.edgeCount()).isEqualTo(graph.edgeCount());
        assertThat(read.pillarCount()).isEqualTo(graph.pillarCount());
        assertThat(read.dataTimestamp()).isEqualTo(TAKEN);
        for (int edge = 0; edge < graph.edgeCount(); edge++) {
            assertThat(read.from(edge)).isEqualTo(graph.from(edge));
            assertThat(read.to(edge)).isEqualTo(graph.to(edge));
            assertThat(read.length(edge)).isEqualTo(graph.length(edge));
            assertThat(read.name(edge)).isEqualTo(graph.name(edge));
            assertThat(read.carSpeedKmh(edge)).isEqualTo(graph.carSpeedKmh(edge));
            assertThat(read.pointCount(edge)).isEqualTo(graph.pointCount(edge));
            for (int point = 0; point < graph.pointCount(edge); point++) {
                assertThat(read.point(edge, point)).isEqualTo(graph.point(edge, point));
            }
            for (int direction : new int[] {RoadGraph.FORWARD, RoadGraph.BACKWARD}) {
                int entry = RoadGraph.entry(edge, direction);
                assertThat(read.allows(entry, TravelMode.CAR)).isEqualTo(graph.allows(entry, TravelMode.CAR));
            }
        }
    }

    @Test
    void aGraphWithoutTimestampKeepsHavingNone() throws IOException {
        NationalGraph.Builder builder = NationalGraph.builder().addNode(1, lat(0), lon(0)).addNode(2, lat(0), lon(1));
        builder.addWay(new RoadWay(1, null, RoadClass.ROAD, 0, false, ALL), new long[] {1, 2});
        java.nio.file.Path file = folder.resolve("tiny.graph");

        NationalGraphFile.write(builder.build(), file);

        assertThat(NationalGraphFile.read(file).dataTimestamp()).isNull();
    }

    @Test
    void refusesFilesThatAreNotGraphsOrAreDamaged() throws IOException {
        java.nio.file.Path text = folder.resolve("notes.graph");
        Files.writeString(text, "these are not the roads you are looking for");
        assertThatThrownBy(() -> NationalGraphFile.read(text)).isInstanceOf(IOException.class)
                .hasMessageContaining("not a national graph file");

        java.nio.file.Path good = folder.resolve("good.graph");
        NationalGraphFile.write(townBuilder().build(), good);
        byte[] bytes = Files.readAllBytes(good);

        java.nio.file.Path truncated = folder.resolve("truncated.graph");
        Files.write(truncated, java.util.Arrays.copyOf(bytes, bytes.length - 10));
        assertThatThrownBy(() -> NationalGraphFile.read(truncated)).isInstanceOf(IOException.class)
                .hasMessageContaining("ends too early");

        byte[] huge = bytes.clone();
        // the tower count follows the 8-byte magic and the 8-byte timestamp
        ByteBuffer.wrap(huge, 16, 4).putInt(Integer.MAX_VALUE);
        java.nio.file.Path damaged = folder.resolve("damaged.graph");
        Files.write(damaged, huge);
        assertThatThrownBy(() -> NationalGraphFile.read(damaged)).isInstanceOf(IOException.class)
                .hasMessageContaining("damaged");

        assertThat(NationalGraphFile.exists(folder.resolve("missing.graph"))).isFalse();
        assertThat(NationalGraphFile.exists(folder)).isFalse();
    }

    @Test
    void findsTheNearestEdgeAndHowFarAlongItAPointLies() {
        NationalGraph graph = townBuilder().build();
        EdgeIndex index = new EdgeIndex(graph);
        int main = edgeOfWay(graph, 100);

        Optional<EdgeSnap> snap = index.nearest(at(0.3, 0.5), 100, edge -> true);

        assertThat(snap).isPresent();
        assertThat(snap.get().edge()).isEqualTo(main);
        assertThat(snap.get().piece()).isZero();
        assertThat(snap.get().offsetMeters()).isCloseTo(GeoMath.distance(at(0, 0), at(0, 0.5)), within(0.5));
        assertThat(snap.get().distanceMeters()).isCloseTo(GeoMath.distance(at(0.3, 0.5), at(0, 0.5)), within(0.5));

        EdgeSnap onShape = index.nearest(at(0.2, 2), 100, edge -> true).orElseThrow();
        assertThat(onShape.point().lat()).isCloseTo(lat(0.2), within(1e-6));
        assertThat(onShape.offsetMeters()).isCloseTo(GeoMath.distance(at(0, 0), at(0, 1))
                + GeoMath.distance(at(0, 1), at(0.2, 2)), within(0.5));

        assertThat(index.nearest(at(3, 0.5), 100, edge -> true)).isEmpty();
        assertThat(index.nearest(at(0.3, 0.5), 100, edge -> edge != main)).isEmpty();
    }

    @Test
    void listsTheEdgesInARectangle() {
        NationalGraph graph = townBuilder().build();
        EdgeIndex index = new EdgeIndex(graph);

        int[] all = index.edgesWithin(lat(-3), lon(-1), lat(1), lon(5), edge -> true, 100);
        assertThat(all).hasSize(graph.edgeCount()).doesNotHaveDuplicates();

        assertThat(index.edgesWithin(lat(-0.1), lon(-0.1), lat(0.1), lon(0.5), edge -> true, 100))
                .contains(edgeOfWay(graph, 100));
        // three kilometres north: no grid cell there holds a road
        assertThat(index.edgesWithin(lat(30), lon(0), lat(31), lon(1), edge -> true, 100)).isEmpty();
        assertThat(index.edgesWithin(lat(-3), lon(-1), lat(1), lon(5), edge -> true, 2)).hasSize(2);
        assertThat(index.edgesWithin(lat(-3), lon(-1), lat(1), lon(5), edge -> false, 100)).isEmpty();
    }

    static int edgeOfWay(NationalGraph graph, long wayId) {
        for (int edge = 0; edge < graph.edgeCount(); edge++) {
            if (graph.wayId(edge) == wayId) {
                return edge;
            }
        }
        throw new AssertionError("no edge of way " + wayId);
    }

    static GeoPoint at(double row, double column) {
        return GraphFixture.at(row, column);
    }

    private static double lat(double row) {
        return GraphFixture.ORIGIN_LAT + row * GraphFixture.ROW;
    }

    private static double lon(double column) {
        return GraphFixture.ORIGIN_LON + column * GraphFixture.COLUMN;
    }
}
