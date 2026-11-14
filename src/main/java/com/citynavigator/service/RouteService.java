package com.citynavigator.service;

import com.citynavigator.dto.RouteResponse;
import com.citynavigator.exception.BusinessRuleException;
import com.citynavigator.exception.UnavailableException;
import com.citynavigator.external.ExternalRoute;
import com.citynavigator.external.ExternalServiceException;
import com.citynavigator.external.OsrmClient;
import com.citynavigator.routing.EdgeSnap;
import com.citynavigator.routing.GeoBounds;
import com.citynavigator.routing.GeoMath;
import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.Optimization;
import com.citynavigator.routing.Path;
import com.citynavigator.routing.RoadGraph;
import com.citynavigator.routing.RoadTraffic;
import com.citynavigator.routing.Snap;
import com.citynavigator.routing.TravelMode;
import com.citynavigator.routing.Weighting;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Plans routes with the best engine for the two points:
 * <ol>
 *     <li>both in the detailed area: the own A* engine on its detailed graph, with incidents and, for cars, traffic;
 *     <li>by car anywhere else in Turkey: the own engine on the national car graph, with traffic, if it is loaded;
 *     <li>otherwise, and when the national graph has no road connection (islands reached by ferry): the nationwide
 *     routing service.
 * </ol>
 * On request the same engine also offers a second, clearly different route: the own engines with the penalty method
 * of {@code routing.Alternatives}, the nationwide service with its own alternatives. Needs no transaction: the own
 * engines work on in-memory graphs.
 */
@ApplicationScoped
public class RouteService {

    /** Points may lie this far outside the detailed area (a click just past its edge still uses the own engine). */
    static final double AREA_MARGIN_METERS = 1_000;
    /** A start or destination further than this from any usable road is rejected. */
    static final double MAX_SNAP_METERS = 300;
    /** Outside the detailed area points are often picked on a coarse map; beyond this they are not near a road. */
    static final double MAX_NATIONWIDE_SNAP_METERS = 1_000;
    static final double MIN_ROUTE_METERS = 5;
    /** Traffic is known by day type and hour, so any time works; these limits only keep requests sensible. */
    static final Duration MAX_DEPARTURE_IN_PAST = Duration.ofDays(1);
    static final Duration MAX_DEPARTURE_AHEAD = Duration.ofDays(7);
    /** At most this many stops between the start and the destination. */
    static final int MAX_STOPS = 3;

    private static final Logger LOGGER = Logger.getLogger(RouteService.class.getName());

    private RoutingEngine engine;
    private NationalEngine national;
    private TrafficService traffic;
    private Clock clock;
    private OsrmClient nationwide;
    private ExternalServices external;

    protected RouteService() {
        // for the CDI proxy
    }

    @Inject
    public RouteService(RoutingEngine engine, NationalEngine national, TrafficService traffic, Clock clock,
                        OsrmClient nationwide, ExternalServices external) {
        this.engine = engine;
        this.national = national;
        this.traffic = traffic;
        this.clock = clock;
        this.nationwide = nationwide;
        this.external = external;
    }

    /**
     * A route through stops, in the given order. Each leg is planned like a route of its own, with the traffic of the
     * time it starts (the departure plus the legs before it), and the legs are joined into one route whose steps say
     * where a stop is reached. Routes with stops get no alternative.
     *
     * @param stops at most {@link #MAX_STOPS} points between the start and the destination; none for a plain route
     */
    public RouteResponse plan(GeoPoint from, List<GeoPoint> stops, GeoPoint to, TravelMode mode,
                              Optimization optimization, Instant departAt, boolean alternative) {
        if (stops.isEmpty()) {
            return plan(from, to, mode, optimization, departAt, alternative);
        }
        if (stops.size() > MAX_STOPS) {
            throw new BusinessRuleException("En fazla " + MAX_STOPS + " ara durak eklenebilir");
        }
        List<GeoPoint> points = new ArrayList<>();
        points.add(from);
        points.addAll(stops);
        points.add(to);
        List<RouteResponse> legs = new ArrayList<>();
        Instant legStart = departure(departAt);
        for (int leg = 0; leg + 1 < points.size(); leg++) {
            RouteResponse planned;
            try {
                planned = plan(points.get(leg), points.get(leg + 1), mode, optimization, legStart, false);
            } catch (BusinessRuleException e) {
                throw new BusinessRuleException(legName(leg, stops.size()) + ": " + e.getMessage());
            }
            legs.add(planned);
            legStart = legStart.plusSeconds(Math.round(planned.durationSeconds()));
        }
        return DtoMapper.joinLegs(legs);
    }

    /** "1. ara durağa giden yol", so that a problem with a stop names the stop. */
    private static String legName(int leg, int stops) {
        if (leg == stops) {
            return (stops == 1 ? "Ara duraktan" : "Son ara duraktan") + " hedefe giden yol";
        }
        return (stops == 1 ? "Ara durağa" : leg + 1 + ". ara durağa") + " giden yol";
    }

    /**
     * The best route alone; see {@link #plan(GeoPoint, GeoPoint, TravelMode, Optimization, Instant, boolean)}.
     */
    public RouteResponse plan(GeoPoint from, GeoPoint to, TravelMode mode, Optimization optimization,
                              Instant departAt) {
        return plan(from, to, mode, optimization, departAt, false);
    }

    /**
     * @param departAt    when the trip starts; {@code null} for now
     * @param alternative whether to look for a second route too: clearly different from the best one and not much
     *                    slower; if there is one it comes in {@link RouteResponse#alternatives()}
     */
    public RouteResponse plan(GeoPoint from, GeoPoint to, TravelMode mode, Optimization optimization,
                              Instant departAt, boolean alternative) {
        Instant departure = departure(departAt);
        RoutingEngine.Snapshot snapshot = engine.snapshot();
        RoadGraph graph = snapshot.graph();
        boolean fromInside = graph.covers(from, AREA_MARGIN_METERS);
        boolean toInside = graph.covers(to, AREA_MARGIN_METERS);
        if (fromInside && toInside) {
            return planInArea(snapshot, from, to, mode, optimization, departure, alternative);
        }
        requireTurkey(from, "Başlangıç");
        requireTurkey(to, "Varış");
        if (mode == TravelMode.CAR) {
            Optional<NationalEngine.Network> network = national.network();
            if (network.isPresent() && network.get().graph().covers(from, AREA_MARGIN_METERS)
                    && network.get().graph().covers(to, AREA_MARGIN_METERS)) {
                Optional<RouteResponse> route = planOnNationalGraph(network.get(), from, to, optimization, departure,
                        alternative);
                if (route.isPresent()) {
                    return route.get();
                }
            }
        }
        if (!external.enabled()) {
            throw new BusinessRuleException((fromInside ? "Varış" : "Başlangıç") + " noktası harita alanının dışında");
        }
        return planNationwide(from, to, mode, departure, alternative);
    }

    private Instant departure(Instant requested) {
        return checkedDeparture(requested, clock.instant());
    }

    /** The departure to plan for: now when none is asked for, otherwise at most a day back and a week ahead. */
    static Instant checkedDeparture(Instant requested, Instant now) {
        if (requested == null) {
            return now;
        }
        if (requested.isBefore(now.minus(MAX_DEPARTURE_IN_PAST))) {
            throw new BusinessRuleException("Kalkış zamanı en fazla 1 gün öncesi olabilir");
        }
        if (requested.isAfter(now.plus(MAX_DEPARTURE_AHEAD))) {
            throw new BusinessRuleException("Kalkış zamanı en fazla 7 gün sonrası olabilir");
        }
        return requested;
    }

    private RouteResponse planInArea(RoutingEngine.Snapshot snapshot, GeoPoint from, GeoPoint to, TravelMode mode,
                                     Optimization optimization, Instant departure, boolean alternative) {
        Snap start = snap(snapshot, from, mode, "Başlangıç");
        Snap goal = snap(snapshot, to, mode, "Varış");
        if (GeoMath.distance(start.point(), goal.point()) < MIN_ROUTE_METERS) {
            throw sameSpot();
        }
        RoadTraffic congestion = mode == TravelMode.CAR ? traffic.forDeparture(departure) : RoadTraffic.NONE;
        Weighting weighting = optimization.weighting(snapshot.graph(), mode, snapshot.traffic(), departure, congestion);
        Path path = snapshot.router().route(start, goal, weighting).orElseThrow(() -> noRoute(mode));
        Function<Path, RouteResponse> describe = found -> DtoMapper.toRoute(mode, optimization, snapshot.graph(),
                start, goal, found, snapshot.instructions().build(found, mode), incidentsOn(snapshot, found, departure),
                departure);
        RouteResponse route = describe.apply(path);
        if (!alternative) {
            return route;
        }
        return route.withAlternatives(snapshot.router().alternative(start, goal, weighting, path).map(describe)
                .stream().toList());
    }

    /**
     * A car route on the national graph; empty when the graph has no road connection between the points or all
     * search slots are busy, so the caller can ask the nationwide service instead.
     */
    private Optional<RouteResponse> planOnNationalGraph(NationalEngine.Network network, GeoPoint from, GeoPoint to,
                                                        Optimization optimization, Instant departure,
                                                        boolean alternative) {
        EdgeSnap start = snap(network, from, "Başlangıç");
        EdgeSnap goal = snap(network, to, "Varış");
        if (GeoMath.distance(start.point(), goal.point()) < MIN_ROUTE_METERS) {
            throw sameSpot();
        }
        Weighting weighting = optimization.weighting(network.graph(), traffic.forDeparture(departure));
        Optional<Path> path;
        try {
            path = national.route(network, start, goal, weighting);
        } catch (UnavailableException e) {
            if (!external.enabled()) {
                throw e;
            }
            LOGGER.info("all national route searches are busy; asking the nationwide service");
            return Optional.empty();
        }
        if (path.isEmpty()) {
            if (!external.enabled()) {
                throw noRoute(TravelMode.CAR);
            }
            return Optional.empty();
        }
        Function<Path, RouteResponse> describe = found -> DtoMapper.toRoute(optimization, network.graph(), start, goal,
                found, network.instructions().build(found, TravelMode.CAR), departure);
        RouteResponse route = describe.apply(path.get());
        if (!alternative) {
            return Optional.of(route);
        }
        return Optional.of(route.withAlternatives(national.alternative(network, start, goal, weighting, path.get())
                .map(describe).stream().toList()));
    }

    private RouteResponse planNationwide(GeoPoint from, GeoPoint to, TravelMode mode, Instant departure,
                                         boolean alternative) {
        List<ExternalRoute> found;
        try {
            found = nationwide.routes(from, to, mode, alternative ? 1 : 0);
        } catch (ExternalServiceException e) {
            LOGGER.log(Level.WARNING, "the nationwide routing service failed", e);
            throw new UnavailableException("Türkiye geneli rota servisine şu an ulaşılamıyor; birazdan tekrar deneyin");
        }
        if (found.isEmpty()) {
            throw noRoute(mode);
        }
        ExternalRoute route = found.getFirst();
        requireNearRoad(route.from(), mode, "Başlangıç");
        requireNearRoad(route.to(), mode, "Varış");
        if (GeoMath.distance(route.from().point(), route.to().point()) < MIN_ROUTE_METERS) {
            throw sameSpot();
        }
        return DtoMapper.toRoute(mode, route, departure).withAlternatives(found.stream().skip(1).limit(1)
                .map(other -> DtoMapper.toRoute(mode, other, departure)).toList());
    }

    private static Snap snap(RoutingEngine.Snapshot snapshot, GeoPoint point, TravelMode mode, String which) {
        RoadGraph graph = snapshot.graph();
        return snapshot.index().nearest(point, MAX_SNAP_METERS, segment -> graph.allowsAny(segment, mode))
                .orElseThrow(() -> new BusinessRuleException(which + " noktasının " + (int) MAX_SNAP_METERS
                        + " m yakınında " + Phrases.roadFor(mode) + " yok"));
    }

    private static EdgeSnap snap(NationalEngine.Network network, GeoPoint point, String which) {
        return network.index().nearest(point, MAX_NATIONWIDE_SNAP_METERS, edge -> true)
                .orElseThrow(() -> new BusinessRuleException(which + " noktasının " + (int) MAX_NATIONWIDE_SNAP_METERS
                        + " m yakınında " + Phrases.roadFor(TravelMode.CAR) + " yok"));
    }

    private static void requireTurkey(GeoPoint point, String which) {
        if (!GeoBounds.TURKEY.contains(point)) {
            throw new BusinessRuleException(which + " noktası Türkiye sınırlarının dışında");
        }
    }

    private static void requireNearRoad(ExternalRoute.Waypoint waypoint, TravelMode mode, String which) {
        if (waypoint.distanceMeters() > MAX_NATIONWIDE_SNAP_METERS) {
            throw new BusinessRuleException(which + " noktasının " + (int) MAX_NATIONWIDE_SNAP_METERS
                    + " m yakınında " + Phrases.roadFor(mode) + " yok");
        }
    }

    private static BusinessRuleException sameSpot() {
        return new BusinessRuleException("Başlangıç ve varış noktası aynı yerde");
    }

    private static BusinessRuleException noRoute(TravelMode mode) {
        return new BusinessRuleException("Bu iki nokta arasında " + Phrases.by(mode)
                + " gidilebilecek bir yol bulunamadı");
    }

    private static List<Long> incidentsOn(RoutingEngine.Snapshot snapshot, Path path, Instant at) {
        Set<Long> incidents = new LinkedHashSet<>();
        for (int piece = 0; piece < path.pieceCount(); piece++) {
            incidents.addAll(snapshot.traffic().incidentsOn(RoadGraph.segmentOf(path.pieceEntry(piece)), at));
        }
        return List.copyOf(incidents);
    }
}
