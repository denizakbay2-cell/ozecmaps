package com.citynavigator.service;

import com.citynavigator.exception.UnavailableException;
import com.citynavigator.routing.EdgeIndex;
import com.citynavigator.routing.EdgeSnap;
import com.citynavigator.routing.InstructionBuilder;
import com.citynavigator.routing.NationalGraph;
import com.citynavigator.routing.NationalGraphFile;
import com.citynavigator.routing.NationalRouter;
import com.citynavigator.routing.Path;
import com.citynavigator.routing.Weighting;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The car road network of all of Turkey, read at start from the file named by {@code NATIONAL_GRAPH_FILE} (built
 * offline by {@code scripts/build-national-graph.sh}). It is optional: without the file, or without enough memory
 * for it, car routes outside the detailed area go to the nationwide routing service as before.
 *
 * <p>A search across the country needs tens of megabytes of working memory, so only a few run at a time.
 */
@ApplicationScoped
public class NationalEngine {

    static final String FILE_SETTING = "NATIONAL_GRAPH_FILE";
    static final int PARALLEL_SEARCHES = 2;
    static final long SEARCH_WAIT_SECONDS = 10;
    /** Heap the graph and its index take per byte of the file (measured: about 1.33). */
    static final double HEAP_PER_FILE_BYTE = 1.4;
    /** Heap the rest of the application and the searches need next to the graph. */
    static final long RESERVED_HEAP_BYTES = 384L << 20;

    private static final Logger LOGGER = Logger.getLogger(NationalEngine.class.getName());

    private final Semaphore searches;
    private final Duration searchWait;
    private volatile Network network;

    public NationalEngine() {
        this(PARALLEL_SEARCHES, Duration.ofSeconds(SEARCH_WAIT_SECONDS));
    }

    NationalEngine(int parallelSearches, Duration searchWait) {
        this.searches = new Semaphore(parallelSearches, true);
        this.searchWait = searchWait;
    }

    /**
     * Reads the file named by {@code NATIONAL_GRAPH_FILE}; if it is not set, missing, unreadable or too big for the
     * heap, the engine stays off and the reason is logged.
     */
    public void load() {
        String setting = System.getenv(FILE_SETTING);
        if (setting == null || setting.isBlank()) {
            LOGGER.info(FILE_SETTING + " is not set: car routes outside the detailed area use the nationwide service");
            return;
        }
        load(java.nio.file.Path.of(setting.strip()), Runtime.getRuntime().maxMemory());
    }

    boolean load(java.nio.file.Path file, long maxHeapBytes) {
        if (!NationalGraphFile.exists(file)) {
            LOGGER.warning(() -> "national road graph " + file + " not found: car routes outside the detailed area "
                    + "use the nationwide service");
            return false;
        }
        try {
            long needed = (long) (java.nio.file.Files.size(file) * HEAP_PER_FILE_BYTE) + RESERVED_HEAP_BYTES;
            if (needed > maxHeapBytes) {
                LOGGER.warning(() -> "national road graph " + file + " needs about " + (needed >> 20) + " MB of heap, "
                        + "only " + (maxHeapBytes >> 20) + " MB available: car routes outside the detailed area use "
                        + "the nationwide service");
                return false;
            }
            long started = System.nanoTime();
            Network loaded = Network.of(NationalGraphFile.read(file));
            network = loaded;
            long millis = (System.nanoTime() - started) / 1_000_000;
            NationalGraph graph = loaded.graph();
            LOGGER.info(() -> "national road graph loaded: " + graph.nodeCount() + " junctions, " + graph.edgeCount()
                    + " roads, data of " + graph.dataTimestamp() + ", " + millis + " ms");
            return true;
        } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "national road graph " + file + " could not be read", e);
            return false;
        }
    }

    public boolean isLoaded() {
        return network != null;
    }

    public Optional<Network> network() {
        return Optional.ofNullable(network);
    }

    /**
     * Searches a route on the loaded network, waiting a little for a free search slot.
     *
     * @throws UnavailableException if all slots stay busy
     */
    public Optional<Path> route(Network on, EdgeSnap start, EdgeSnap goal, Weighting weighting) {
        boolean acquired;
        try {
            acquired = searches.tryAcquire(searchWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UnavailableException("Rota araması yarıda kesildi; tekrar deneyin");
        }
        if (!acquired) {
            throw new UnavailableException("Rota motoru şu an çok yoğun; birazdan tekrar deneyin");
        }
        try {
            return on.router().route(start, goal, weighting);
        } finally {
            searches.release();
        }
    }

    /**
     * Searches an alternative to {@code best}, but only if a search slot is free right away: an alternative is a
     * nicety, and on a busy server the slots are kept for first routes.
     */
    public Optional<Path> alternative(Network on, EdgeSnap start, EdgeSnap goal, Weighting weighting, Path best) {
        try {
            // with a zero timeout, unlike tryAcquire(), the queue of waiting first routes is respected
            if (!searches.tryAcquire(0, TimeUnit.MILLISECONDS)) {
                return Optional.empty();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        try {
            return on.router().alternative(start, goal, weighting, best);
        } finally {
            searches.release();
        }
    }

    /**
     * The loaded network with what searches on it need.
     */
    public record Network(NationalGraph graph, EdgeIndex index, NationalRouter router, InstructionBuilder instructions) {

        static Network of(NationalGraph graph) {
            return new Network(graph, new EdgeIndex(graph), new NationalRouter(graph), new InstructionBuilder(graph));
        }
    }

    /** For tests: use this network as if it had been read from a file. */
    void use(NationalGraph graph) {
        network = Network.of(graph);
    }
}
