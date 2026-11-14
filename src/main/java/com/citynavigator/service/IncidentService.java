package com.citynavigator.service;

import com.citynavigator.dto.IncidentRequest;
import com.citynavigator.dto.IncidentView;
import com.citynavigator.entity.Incident;
import com.citynavigator.entity.IncidentType;
import com.citynavigator.entity.SegmentRef;
import com.citynavigator.exception.BusinessRuleException;
import com.citynavigator.exception.ResourceNotFoundException;
import com.citynavigator.repository.IncidentRepository;
import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.RoadFootprint;
import com.citynavigator.routing.RoadGraph;
import com.citynavigator.routing.Snap;
import com.citynavigator.routing.TravelMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Reporting and resolving road incidents.
 *
 * <p>Reporting is one transaction: the incident and every segment it covers are stored together or not at all.
 * Only after the commit does the routing engine hear about it ({@link IncidentChanged}), so routes and database
 * never disagree.
 */
@ApplicationScoped
@Transactional
public class IncidentService {

    /** An incident has to be reported on a road a car can use, at most this far from the clicked point. */
    static final double MAX_SNAP_METERS = 60;

    private IncidentRepository incidents;
    private RoutingEngine engine;
    private Event<IncidentChanged> changes;
    private Clock clock;

    protected IncidentService() {
        // for the CDI proxy
    }

    @Inject
    public IncidentService(IncidentRepository incidents, RoutingEngine engine, Event<IncidentChanged> changes,
                           Clock clock) {
        this.incidents = incidents;
        this.engine = engine;
        this.changes = changes;
        this.clock = clock;
    }

    public List<IncidentView> active() {
        RoadGraph graph = engine.snapshot().graph();
        return incidents.findActive(clock.instant()).stream()
                .map(incident -> DtoMapper.toView(incident, graph))
                .toList();
    }

    public IncidentView get(long id) {
        return DtoMapper.toView(find(id), engine.snapshot().graph());
    }

    public IncidentView report(IncidentRequest request) {
        IncidentType type = IncidentType.valueOf(request.type());
        RoutingEngine.Snapshot snapshot = engine.snapshot();
        RoadGraph graph = snapshot.graph();
        GeoPoint point = new GeoPoint(request.lat(), request.lon());
        if (!graph.covers(point, RouteService.AREA_MARGIN_METERS)) {
            throw new BusinessRuleException("Olay bildirimi şimdilik yalnızca ayrıntılı yol haritası olan bölgede yapılabiliyor");
        }
        Snap snap = snapshot.index()
                .nearest(point, MAX_SNAP_METERS, segment -> graph.allowsAny(segment, TravelMode.CAR))
                .orElseThrow(() -> new BusinessRuleException("Olay bir araç yolunun üzerinde bildirilmeli; bu noktanın "
                        + (int) MAX_SNAP_METERS + " m yakınında araç yolu yok"));
        Set<Integer> segments = type.closesRoad()
                ? RoadFootprint.betweenJunctions(graph, snap, type.reachMeters(), TravelMode.CAR)
                : RoadFootprint.alongRoad(graph, snap, type.reachMeters(), TravelMode.CAR);
        Set<SegmentRef> refs = new LinkedHashSet<>();
        for (int segment : segments) {
            refs.add(new SegmentRef(graph.wayId(segment), graph.sequence(segment)));
        }
        Duration duration = request.durationMinutes() == null ? null : Duration.ofMinutes(request.durationMinutes());
        Incident incident = incidents.add(Incident.report(type, request.description(),
                new double[] {DtoMapper.round(snap.point().lat()), DtoMapper.round(snap.point().lon())},
                graph.way(snap.segment()).name(), refs, clock.instant(), duration));
        changes.fire(new IncidentChanged(incident.getId(), RoutingEngine.restrictionOf(incident),
                List.copyOf(segments)));
        return DtoMapper.toView(incident, graph);
    }

    public IncidentView resolve(long id) {
        Incident incident = find(id);
        incident.resolve(clock.instant());
        incidents.flush();
        changes.fire(IncidentChanged.resolved(id));
        return DtoMapper.toView(incident, engine.snapshot().graph());
    }

    public boolean anyRecorded() {
        return !incidents.isEmpty();
    }

    private Incident find(long id) {
        return incidents.findById(id).orElseThrow(() -> new ResourceNotFoundException("Olay", id));
    }
}
