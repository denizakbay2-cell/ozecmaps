package com.citynavigator.osm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OverpassReaderTest {

    @Test
    void streamsEveryElementWithItsPositionNodesAndTags() {
        List<OsmElement> elements = new ArrayList<>();
        Optional<Instant> timestamp = OverpassReader.read(resource("osm/tiny-places.json"), elements::add);

        assertThat(timestamp).isEmpty();
        assertThat(elements).hasSize(6);
        OsmElement pier = elements.get(0);
        assertThat(pier.type()).isEqualTo("node");
        assertThat(pier.tags()).containsEntry("name", "Kadıköy İskelesi");
        assertThat(pier.lat()).isEqualTo(40.9913);
        OsmElement park = elements.get(1);
        assertThat(park.hasPosition()).isTrue();
        assertThat(park.lon()).isEqualTo(29.0250);
        assertThat(elements.get(4).hasPosition()).isFalse();
    }

    @Test
    void readsTheDataTimestampAndWayNodes() {
        List<OsmElement> elements = new ArrayList<>();
        Optional<Instant> timestamp = OverpassReader.read(resource("osm/tiny-roads.json"), elements::add);

        assertThat(timestamp).contains(Instant.parse("2026-11-14T10:58:36Z"));
        OsmElement way = elements.get(0);
        assertThat(way.nodes()).containsExactly(1L, 2L, 2L, 3L);
        assertThat(way.distinctConsecutiveNodes()).containsExactly(1L, 2L, 3L);
        assertThat(way.hasPosition()).isFalse();
    }

    @Test
    void anUnreadableTimestampIsLeftOut() {
        String json = "{\"osm3s\": {\"timestamp_osm_base\": \"yesterday\"}, \"elements\": []}";
        assertThat(OverpassReader.read(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), e -> { }))
                .isEmpty();
    }

    static InputStream resource(String name) {
        return OverpassReaderTest.class.getClassLoader().getResourceAsStream(name);
    }
}
