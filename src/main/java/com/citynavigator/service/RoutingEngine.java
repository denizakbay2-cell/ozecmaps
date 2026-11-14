package com.citynavigator.service;

import com.citynavigator.entity.Incident;
import com.citynavigator.entity.SegmentRef;
import com.citynavigator.repository.IncidentRepository;
import com.citynavigator.repository.RoadNetworkRepository;
import com.citynavigator.routing.AStarRouter;
import com.citynavigator.routing.InstructionBuilder;
import com.citynavigator.routing.RoadGraph;
import com.citynavigator.routing.SpatialIndex;
import com.citynavigator.routing.TrafficConditions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

/**
 * Holds the routing graph in memory and keeps its traffic picture in step with the incidents in the database.
 *
 * <p>The database is the source of truth; the {@link Snapshot} is a read model built from it. Requests read the
 * current snapshot once and work on it; an incident change swaps in a new snapshot, so no request ever sees half
 * an update and no locking is needed on the read path.
 */
@ApplicationScoped
public class RoutingEngine {

    private static final Logger LOGGER = Logger.getLogger(RoutingEngine.class.getName());

    private RoadNetworkRepository network;
    private IncidentRepository incidents;
    private Clock clock;
    private volatile Snapshot snapshot;

    protected RoutingEngine() {
        // for the CDI proxy
    }

    @Inject
    public RoutingEngine(RoadNetworkRepository network, IncidentRepository incidents, Clock clock) {
        this.network = network;
        this.incidents = incidents;
        this.clock = clock;
    }

    /**
     * Reads the road network and the active incidents; called once at start.
     */
    @Transactional
    public void load() {
        long started = System.nanoTime();
        RoadGraph graph = network.loadGraph();
        TrafficConditions traffic = TrafficConditions.NONE;
        for (Incident incident : incidents.findActive(clock.instant())) {
            traffic = traffic.with(restrictionOf(incident), segmentsOf(graph, incident.getSegments()));
        }
        Snapshot loaded = Snapshot.of(graph, traffic);
        snapshot = loaded;
        long millis = (System.nanoTime() - started) / 1_000_000;
        LOGGER.info(() -> "routing graph loaded: " + graph.nodeCount() + " nodes, " + graph.segmentCount()
                + " segments, " + loaded.traffic().restrictedSegmentCount() + " restricted segments, " + millis + " ms");
    }

    /**
     * Whether routes can be served: the network has been read into memory.
     */
    public boolean isLoaded() {
        return snapshot != null;
    }

    public Snapshot snapshot() {
        Snapshot current = snapshot;
        if (current == null) {
            throw new IllegalStateException("the road network has not been loaded yet");
        }
        return current;
    }

    void onIncidentChanged(@Observes(during = TransactionPhase.AFTER_SUCCESS) IncidentChanged change) {
        apply(change);
    }

    synchronized void apply(IncidentChanged change) {
        Snapshot current = snapshot;
        if (current == null) {
            return;
        }
        TrafficConditions traffic = change.active()
                ? current.traffic().with(change.restriction(), change.segments())
                : current.traffic().without(change.incidentId());
        snapshot = current.withTraffic(traffic);
    }

    static TrafficConditions.Restriction restrictionOf(Incident incident) {
        return incident.getType().closesRoad()
                ? TrafficConditions.Restriction.closure(incident.getId(), incident.getEndsAt().orElse(null))
                : TrafficConditions.Restriction.slowdown(incident.getId(), incident.getType().speedFactor(),
                        incident.getEndsAt().orElse(null));
    }

    /**
     * Graph segments for stored references; references the graph does not know are skipped.
     */
    static List<Integer> segmentsOf(RoadGraph graph, Collection<SegmentRef> refs) {
        List<Integer> segments = new ArrayList<>(refs.size());
        for (SegmentRef ref : refs) {
            int segment = graph.segment(ref.getWayId(), ref.getSequence());
            if (segment >= 0) {
                segments.add(segment);
            }
        }
        return segments;
    }

    /**
     * Everything a route request needs, consistent with itself.
     */
    public record Snapshot(RoadGraph graph, SpatialIndex index, AStarRouter router, InstructionBuilder instructions,
                           TrafficConditions traffic) {

        public static Snapshot of(RoadGraph graph, TrafficConditions traffic) {
            return new Snapshot(graph, new SpatialIndex(graph), new AStarRouter(graph), new InstructionBuilder(graph),
                    traffic);
        }

        Snapshot withTraffic(TrafficConditions newTraffic) {
            return new Snapshot(graph, index, router, instructions, newTraffic);
        }
    }
}
