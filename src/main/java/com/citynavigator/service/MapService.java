package com.citynavigator.service;

import com.citynavigator.dto.MapInfo;
import com.citynavigator.dto.NationalGraphInfo;
import com.citynavigator.entity.MapArea;
import com.citynavigator.repository.MapAreaRepository;
import com.citynavigator.routing.GeoBounds;
import com.citynavigator.routing.NationalGraph;
import com.citynavigator.routing.RoadGraph;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Arrays;
import java.util.List;

/**
 * Describes the detailed area, and the nationwide reach and traffic data beyond it, for the map client.
 */
@ApplicationScoped
@Transactional
public class MapService {

    static final String ATTRIBUTION = "Harita verisi © OpenStreetMap katkıda bulunanlar (ODbL)";
    private static final GeoBounds NATIONWIDE = GeoBounds.TURKEY;

    private MapAreaRepository areas;
    private RoutingEngine engine;
    private ExternalServices external;
    private NationalEngine national;
    private TrafficService traffic;

    protected MapService() {
        // for the CDI proxy
    }

    @Inject
    public MapService(MapAreaRepository areas, RoutingEngine engine, ExternalServices external,
                      NationalEngine national, TrafficService traffic) {
        this.areas = areas;
        this.engine = engine;
        this.external = external;
        this.national = national;
        this.traffic = traffic;
    }

    public MapInfo info() {
        MapArea area = areas.find().orElseThrow(() -> new IllegalStateException("no map area has been imported"));
        RoadGraph graph = engine.snapshot().graph();
        return new MapInfo(area.getName(), Arrays.stream(area.getBounds()).boxed().toList(),
                Arrays.stream(area.getCenter()).boxed().toList(), graph.nodeCount(), graph.segmentCount(),
                graph.wayCount(), area.getDataTimestamp().orElse(null), area.getImportedAt(), ATTRIBUTION,
                external.enabled() || national.isLoaded(), List.of(NATIONWIDE.minLat(), NATIONWIDE.minLon(),
                        NATIONWIDE.maxLat(), NATIONWIDE.maxLon()),
                national.network().map(network -> info(network.graph())).orElse(null),
                traffic.profile().cellCount());
    }

    private static NationalGraphInfo info(NationalGraph graph) {
        return new NationalGraphInfo(graph.nodeCount(), graph.edgeCount(), graph.wayCount(), graph.dataTimestamp());
    }
}
