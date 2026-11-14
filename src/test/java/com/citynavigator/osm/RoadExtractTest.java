package com.citynavigator.osm;

import static org.assertj.core.api.Assertions.assertThat;

import com.citynavigator.routing.RoadClass;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RoadExtractTest {

    private final RoadExtract extract = RoadExtract.read(OverpassReaderTest.resource("osm/tiny-roads.json"));

    @Test
    void keepsRoutableWaysWithCleanedNodeLists() {
        assertThat(extract.ways()).extracting(way -> way.way().id()).containsExactly(10L, 11L);
        RoadExtract.Way moda = extract.ways().get(0);
        assertThat(moda.nodes()).containsExactly(1L, 2L, 3L);
        assertThat(moda.way().maxSpeedKmh()).isEqualTo(30);
        assertThat(extract.ways().get(1).way().roadClass()).isEqualTo(RoadClass.FOOTWAY);
    }

    @Test
    void dropsWaysWithMissingNodesAndNodesNoWayUses() {
        assertThat(extract.skippedWays()).isEqualTo(1);
        assertThat(extract.nodes()).extracting(RoadExtract.Node::id).containsExactly(1L, 2L, 3L, 4L);
        assertThat(extract.timestamp()).contains(Instant.parse("2026-11-14T10:58:36Z"));
    }
}
