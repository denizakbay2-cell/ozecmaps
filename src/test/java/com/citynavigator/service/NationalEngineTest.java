package com.citynavigator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.citynavigator.exception.UnavailableException;
import com.citynavigator.routing.EdgeSnap;
import com.citynavigator.routing.NationalGraph;
import com.citynavigator.routing.NationalGraphFile;
import com.citynavigator.routing.Optimization;
import com.citynavigator.routing.RoadTraffic;
import com.citynavigator.routing.Weighting;
import com.citynavigator.testsupport.GraphFixture;
import com.citynavigator.testsupport.NationalFixture;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NationalEngineTest {

    private static final long ONE_GIGABYTE = 1L << 30;

    @TempDir
    Path folder;

    private Path townFile() throws IOException {
        Path file = folder.resolve("town.graph");
        NationalGraphFile.write(NationalFixture.of(ServiceFixture.town()), file);
        return file;
    }

    @Test
    void readsTheGraphFileWhenTheHeapIsBigEnough() throws IOException {
        NationalEngine engine = new NationalEngine();
        assertThat(engine.isLoaded()).isFalse();
        assertThat(engine.network()).isEmpty();

        assertThat(engine.load(townFile(), ONE_GIGABYTE)).isTrue();

        assertThat(engine.isLoaded()).isTrue();
        NationalGraph graph = engine.network().orElseThrow().graph();
        assertThat(graph.nodeCount()).isEqualTo(6);
    }

    @Test
    void staysOffWithoutTheFileWithoutMemoryOrWithADamagedFile() throws IOException {
        NationalEngine engine = new NationalEngine();
        Path damaged = folder.resolve("damaged.graph");
        Files.writeString(damaged, "not a graph");

        assertThat(engine.load(folder.resolve("missing.graph"), ONE_GIGABYTE)).isFalse();
        assertThat(engine.load(townFile(), 100L << 20)).isFalse();
        assertThat(engine.load(damaged, ONE_GIGABYTE)).isFalse();
        // NATIONAL_GRAPH_FILE is not set while the tests run
        engine.load();

        assertThat(engine.isLoaded()).isFalse();
    }

    @Test
    void searchesOnlyWhenASlotIsFree() {
        NationalEngine busy = new NationalEngine(0, Duration.ofMillis(20));
        busy.use(NationalFixture.of(ServiceFixture.town()));
        NationalEngine.Network network = busy.network().orElseThrow();
        EdgeSnap from = network.index().nearest(GraphFixture.at(0, 0.5), 50, edge -> true).orElseThrow();
        EdgeSnap to = network.index().nearest(GraphFixture.at(0, 1.5), 50, edge -> true).orElseThrow();
        Weighting fastest = Optimization.FASTEST.weighting(network.graph(), RoadTraffic.NONE);

        assertThatThrownBy(() -> busy.route(network, from, to, fastest))
                .isInstanceOf(UnavailableException.class).hasMessageContaining("yoğun");

        NationalEngine free = new NationalEngine();
        assertThat(free.route(network, from, to, fastest)).isPresent();
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> free.route(network, from, to, fastest))
                    .isInstanceOf(UnavailableException.class).hasMessageContaining("kesildi");
        } finally {
            assertThat(Thread.interrupted()).isTrue();
        }
    }
}
