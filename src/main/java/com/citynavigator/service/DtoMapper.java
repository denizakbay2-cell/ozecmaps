package com.citynavigator.service;

import com.citynavigator.dto.IncidentView;
import com.citynavigator.dto.InstructionView;
import com.citynavigator.dto.PlaceView;
import com.citynavigator.dto.RouteResponse;
import com.citynavigator.dto.SnappedPoint;
import com.citynavigator.dto.TrafficStretch;
import com.citynavigator.entity.Incident;
import com.citynavigator.entity.Place;
import com.citynavigator.entity.PlaceCategory;
import com.citynavigator.entity.PlaceDetails;
import com.citynavigator.entity.SegmentRef;
import com.citynavigator.external.ExternalRoute;
import com.citynavigator.external.GeocodedPlace;
import com.citynavigator.routing.EdgeSnap;
import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.Instruction;
import com.citynavigator.routing.NationalGraph;
import com.citynavigator.routing.Optimization;
import com.citynavigator.routing.Path;
import com.citynavigator.routing.RoadGraph;
import com.citynavigator.routing.Snap;
import com.citynavigator.routing.TravelMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Entities and routing results to DTOs. Coordinates are rounded to six decimals (about 10 cm), which keeps
 * responses small without visible loss.
 */
final class DtoMapper {

    static final String OWN_ENGINE = "astar";
    static final String NATIONAL_ENGINE = "national";
    static final String NATIONWIDE_ENGINE = "osrm";

    private DtoMapper() {
    }

    static RouteResponse toRoute(TravelMode mode, Optimization optimization, RoadGraph graph, Snap start, Snap goal,
                                 Path path, List<Instruction> instructions, List<Long> incidentIds, Instant departAt) {
        return new RouteResponse(name(mode), name(optimization), OWN_ENGINE, Math.round(path.distanceMeters()),
                Math.round(path.durationSeconds()), departAt, trafficDelay(path), toSnapped(graph, start),
                toSnapped(graph, goal), geometry(path.points()), slowStretches(path), steps(instructions, path.points()),
                incidentIds, path.visitedNodes(), List.of());
    }

    /** A car route on the national graph. */
    static RouteResponse toRoute(Optimization optimization, NationalGraph graph, EdgeSnap start, EdgeSnap goal,
                                 Path path, List<Instruction> instructions, Instant departAt) {
        return new RouteResponse(name(TravelMode.CAR), name(optimization), NATIONAL_ENGINE,
                Math.round(path.distanceMeters()), Math.round(path.durationSeconds()), departAt, trafficDelay(path),
                toSnapped(graph, start), toSnapped(graph, goal), geometry(path.points()), slowStretches(path),
                steps(instructions, path.points()), List.of(), path.visitedNodes(), List.of());
    }

    /** A route from the nationwide service; it always minimises time and knows nothing of traffic or incidents. */
    static RouteResponse toRoute(TravelMode mode, ExternalRoute route, Instant departAt) {
        return new RouteResponse(name(mode), name(Optimization.FASTEST), NATIONWIDE_ENGINE,
                Math.round(route.distanceMeters()), Math.round(route.durationSeconds()), departAt, null,
                toSnapped(route.from()), toSnapped(route.to()), geometry(route.points()), List.of(),
                steps(route.instructions(), route.points()), List.of(), 0, List.of());
    }

    /**
     * The stretches of the path that traffic or incidents slow below 80 % of the free-flow speed, split where the
     * speed crosses 60 % or 40 %: what the route's colours show.
     */
    static List<TrafficStretch> slowStretches(Path path) {
        List<TrafficStretch> stretches = new ArrayList<>();
        int start = -1;
        int level = 0;
        double seconds = 0;
        double freeFlow = 0;
        for (int piece = 0; piece <= path.pieceCount(); piece++) {
            int pieceLevel = piece == path.pieceCount() ? 0 : slowness(path, piece);
            if (pieceLevel != level && start >= 0) {
                stretches.add(new TrafficStretch(start, piece, Math.round(freeFlow / seconds * 100) / 100.0));
                start = -1;
            }
            if (pieceLevel > 0 && start < 0) {
                start = piece;
                seconds = 0;
                freeFlow = 0;
            }
            if (pieceLevel > 0) {
                seconds += path.pieceSeconds(piece);
                freeFlow += path.pieceFreeFlowSeconds(piece);
            }
            level = pieceLevel;
        }
        return stretches;
    }

    /** 0 for at least 80 % of the free-flow speed, 1 from 60 %, 2 from 40 %, 3 below. */
    private static int slowness(Path path, int piece) {
        double seconds = path.pieceSeconds(piece);
        double share = seconds <= 0 ? 1 : path.pieceFreeFlowSeconds(piece) / seconds;
        return share >= 0.8 ? 0 : share >= 0.6 ? 1 : share >= 0.4 ? 2 : 3;
    }

    /** Whole seconds traffic and incidents add to the trip; never negative. */
    static long trafficDelay(Path path) {
        return Math.max(0, Math.round(path.durationSeconds() - path.freeFlowSeconds()));
    }

    static SnappedPoint toSnapped(RoadGraph graph, Snap snap) {
        return new SnappedPoint(round(snap.point().lat()), round(snap.point().lon()),
                graph.way(snap.segment()).name(), Math.round(snap.distanceMeters()));
    }

    static SnappedPoint toSnapped(NationalGraph graph, EdgeSnap snap) {
        return new SnappedPoint(round(snap.point().lat()), round(snap.point().lon()), graph.name(snap.edge()),
                Math.round(snap.distanceMeters()));
    }

    private static SnappedPoint toSnapped(ExternalRoute.Waypoint waypoint) {
        return new SnappedPoint(round(waypoint.point().lat()), round(waypoint.point().lon()), waypoint.name(),
                Math.round(waypoint.distanceMeters()));
    }

    private static List<double[]> geometry(List<GeoPoint> points) {
        return points.stream().map(DtoMapper::pair).toList();
    }

    private static List<InstructionView> steps(List<Instruction> instructions, List<GeoPoint> points) {
        return instructions.stream().map(step -> {
            GeoPoint at = points.get(step.pointIndex());
            return new InstructionView(step.maneuver().name(), Phrases.instruction(step),
                    Phrases.streetLabel(step.street(), step.roadClass()), step.exitNumber(), step.pointIndex(),
                    round(at.lat()), round(at.lon()), Math.round(step.distanceMeters()),
                    Math.round(step.durationSeconds()));
        }).toList();
    }

    static PlaceView toView(Place place) {
        return toView(place, null);
    }

    /**
     * @param address where the place lies, for example "Bahariye Caddesi, Osmanağa"; tells branches of a chain apart
     */
    static PlaceView toView(Place place, String address) {
        PlaceDetails details = place.getDetails();
        return new PlaceView(place.getId(), place.getName(), place.getCategory().name(), place.getCategory().label(),
                details.kind(), address, place.getLat(), place.getLon(), place.isFromOpenStreetMap(), place.isBusiness(),
                details.openingHours(), details.phone(), details.website());
    }

    /** A nationwide search result, categorised with the same tables as the bundled data. */
    static PlaceView toView(GeocodedPlace place) {
        Map<String, String> tags = place.osmKey() == null || place.osmValue() == null ? Map.of()
                : Map.of(place.osmKey(), place.osmValue());
        Optional<OsmKinds.Kind> business = OsmKinds.businessOf(tags);
        PlaceCategory category = business.map(OsmKinds.Kind::category)
                .or(() -> OsmPlaces.categoryOf(tags))
                .orElse("highway".equals(place.osmKey()) ? PlaceCategory.STREET : PlaceCategory.OTHER);
        return new PlaceView(null, place.name(), category.name(), category.label(),
                business.map(OsmKinds.Kind::label).orElse(null), place.detail(), round(place.lat()),
                round(place.lon()), true, business.isPresent(), null, null, null);
    }

    static IncidentView toView(Incident incident, RoadGraph graph) {
        // the stored set has no order; list the road pieces way by way, as they run
        List<SegmentRef> refs = incident.getSegments().stream()
                .sorted(Comparator.comparingLong(SegmentRef::getWayId).thenComparingInt(SegmentRef::getSequence))
                .toList();
        List<List<double[]>> lines = new ArrayList<>();
        for (int segment : RoutingEngine.segmentsOf(graph, refs)) {
            lines.add(List.of(pair(graph.point(graph.from(segment))), pair(graph.point(graph.to(segment)))));
        }
        return new IncidentView(incident.getId(), incident.getType().name(), incident.getType().label(),
                incident.getStatus().name(), incident.getDescription(), incident.getStreet(), incident.getLat(),
                incident.getLon(), incident.getReportedAt(), incident.getEndsAt().orElse(null), lines);
    }

    private static String name(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    private static double[] pair(GeoPoint point) {
        return new double[] {round(point.lat()), round(point.lon())};
    }

    static double round(double coordinate) {
        return Math.round(coordinate * 1e6) / 1e6;
    }
}
