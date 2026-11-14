package com.citynavigator.service;

import com.citynavigator.dto.TransitLegView;
import com.citynavigator.dto.TransitResponse;
import com.citynavigator.exception.BusinessRuleException;
import com.citynavigator.exception.UnavailableException;
import com.citynavigator.routing.GeoMath;
import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.TransitJourney;
import com.citynavigator.routing.TransitMode;
import com.citynavigator.routing.TransitNetwork;
import com.citynavigator.routing.TransitNetworkFile;
import com.citynavigator.routing.TransitRouter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Journeys by public transport in Istanbul: ferries, metro, Marmaray, tram, Metrobüs, buses, minibuses and dolmuşes.
 * The timetables are built from İBB's open GTFS data by {@code scripts/build-transit.sh} and ship with the application
 * (see ADR-0013); they are read on first use.
 */
@ApplicationScoped
public class TransitService {

    static final String NETWORK_RESOURCE = "/transit/istanbul-transit.bin.gz";
    static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    /** One more vehicle has to save at least this much time to be worth the change (s). */
    static final int RIDE_PENALTY_SECONDS = 300;
    /** The start and the destination may lie this far beyond the outermost stops. */
    static final double COVER_MARGIN_METERS = 2_000;
    static final int MAX_ALTERNATIVES = 3;
    /** Vehicles that do not get stuck in traffic: on rails, on the sea, or in a lane of their own. */
    static final Set<TransitMode> TRAFFIC_FREE = EnumSet.of(TransitMode.FERRY, TransitMode.METRO, TransitMode.RAIL,
            TransitMode.TRAM, TransitMode.FUNICULAR, TransitMode.CABLE_CAR, TransitMode.METROBUS);
    /** A journey free of traffic is offered too when it arrives at most this much later than the best one (s). */
    static final int TRAFFIC_FREE_SLACK_SECONDS = 30 * 60;
    static final String WALK = "WALK";
    static final Map<TransitMode, String> LABELS = new EnumMap<>(Map.of(
            TransitMode.FERRY, "Vapur", TransitMode.METRO, "Metro", TransitMode.RAIL, "Marmaray",
            TransitMode.TRAM, "Tramvay", TransitMode.FUNICULAR, "Füniküler", TransitMode.CABLE_CAR, "Teleferik",
            TransitMode.BUS, "Otobüs", TransitMode.METROBUS, "Metrobüs", TransitMode.MINIBUS, "Minibüs",
            TransitMode.DOLMUS, "Dolmuş"));

    private static final Logger LOGGER = Logger.getLogger(TransitService.class.getName());

    private Clock clock;
    private volatile TransitRouter router;

    protected TransitService() {
        // for the CDI proxy
    }

    @Inject
    public TransitService(Clock clock) {
        this.clock = clock;
    }

    /** For tests: plans on the given network instead of the bundled one. */
    TransitService(Clock clock, TransitNetwork network) {
        this.clock = clock;
        this.router = new TransitRouter(network);
    }

    /**
     * The best journey leaving at {@code departAt} (now when {@code null}), with up to three others: faster ones that
     * change more often, ones that change less but take longer, and the best one on ferries, rails and Metrobüs, which
     * do not get stuck in traffic, when it is not much later. An extra vehicle has to save five minutes to be preferred;
     * when walking is about as fast, walking is the answer.
     */
    public TransitResponse plan(GeoPoint from, GeoPoint to, Instant departAt) {
        TransitRouter planner = router();
        TransitNetwork network = planner.network();
        if (!network.covers(from, COVER_MARGIN_METERS) || !network.covers(to, COVER_MARGIN_METERS)) {
            throw new BusinessRuleException("Toplu taşıma rotası şimdilik yalnızca İstanbul içinde hesaplanabiliyor");
        }
        if (GeoMath.distance(from, to) < RouteService.MIN_ROUTE_METERS) {
            throw new BusinessRuleException("Başlangıç ve varış noktası aynı yerde");
        }
        ZonedDateTime departure = RouteService.checkedDeparture(departAt, clock.instant()).atZone(ISTANBUL);
        ZonedDateTime midnight = departure.toLocalDate().atStartOfDay(ISTANBUL);
        int seconds = (int) (departure.toEpochSecond() - midnight.toEpochSecond());
        List<TransitJourney> journeys = planner.plan(from, to, departure.getDayOfWeek().getValue() - 1, seconds);
        if (journeys.isEmpty()) {
            throw new BusinessRuleException("Bu saatte toplu taşımayla gidilebilecek bir yol bulunamadı");
        }
        TransitJourney best = journeys.stream()
                .min(Comparator.comparingInt(journey -> journey.arrival() + RIDE_PENALTY_SECONDS * journey.rides()))
                .orElseThrow();
        List<TransitJourney> others = new ArrayList<>(journeys.stream().filter(journey -> journey != best)
                .sorted(Comparator.comparingInt(TransitJourney::arrival)).toList());
        planner.plan(from, to, departure.getDayOfWeek().getValue() - 1, seconds, TRAFFIC_FREE).stream()
                .filter(journey -> journey.rides() > 0 && journey.arrival() <= best.arrival() + TRAFFIC_FREE_SLACK_SECONDS)
                .min(Comparator.comparingInt(journey -> journey.arrival() + RIDE_PENALTY_SECONDS * journey.rides()))
                .filter(steady -> !steady.legs().equals(best.legs())
                        && others.stream().noneMatch(other -> other.legs().equals(steady.legs())))
                .ifPresent(others::add);
        return view(network, best, from, to, midnight, others.stream().limit(MAX_ALTERNATIVES)
                .map(journey -> view(network, journey, from, to, midnight, List.of())).toList());
    }

    private static TransitResponse view(TransitNetwork network, TransitJourney journey, GeoPoint from, GeoPoint to,
                                        ZonedDateTime midnight, List<TransitResponse> alternatives) {
        List<TransitLegView> legs = new ArrayList<>();
        long walked = 0;
        for (TransitJourney.Leg leg : journey.legs()) {
            Instant leave = midnight.toInstant().plusSeconds(leg.departure());
            Instant arrive = midnight.toInstant().plusSeconds(leg.arrival());
            long seconds = leg.arrival() - leg.departure();
            switch (leg) {
                case TransitJourney.Walk walk -> {
                    GeoPoint start = walk.fromStop() < 0 ? from : network.stop(walk.fromStop());
                    GeoPoint end = walk.toStop() < 0 ? to : network.stop(walk.toStop());
                    walked += Math.round(walk.meters());
                    legs.add(new TransitLegView(WALK, "Yürüyüş", null, null, null, stopName(network, walk.fromStop()),
                            stopName(network, walk.toStop()), leave, arrive, seconds, Math.round(walk.meters()), 0,
                            List.of(point(start), point(end))));
                }
                case TransitJourney.Ride ride -> {
                    int line = network.patternLine(ride.pattern());
                    TransitMode mode = network.lineMode(line);
                    List<double[]> geometry = new ArrayList<>();
                    double meters = 0;
                    GeoPoint previous = null;
                    for (GeoPoint along : network.rideLine(ride.pattern(), ride.boardIndex(), ride.alightIndex())) {
                        meters += previous == null ? 0 : GeoMath.distance(previous, along);
                        geometry.add(point(along));
                        previous = along;
                    }
                    String code = network.lineName(line);
                    legs.add(new TransitLegView(mode.name(), LABELS.get(mode), code.isEmpty() ? null : code,
                            network.lineLongName(line), network.patternHeadsign(ride.pattern()),
                            network.stopName(network.patternStop(ride.pattern(), ride.boardIndex())),
                            network.stopName(network.patternStop(ride.pattern(), ride.alightIndex())), leave, arrive,
                            seconds, Math.round(meters), ride.alightIndex() - ride.boardIndex(), geometry));
                }
            }
        }
        return new TransitResponse("transit", midnight.toInstant().plusSeconds(journey.departure()),
                midnight.toInstant().plusSeconds(journey.arrival()), journey.durationSeconds(), journey.rides(),
                walked, legs, network.source(), alternatives);
    }

    private static String stopName(TransitNetwork network, int stop) {
        return stop < 0 ? null : network.stopName(stop);
    }

    private static double[] point(GeoPoint point) {
        return new double[] {DtoMapper.round(point.lat()), DtoMapper.round(point.lon())};
    }

    private TransitRouter router() {
        TransitRouter known = router;
        if (known == null) {
            synchronized (this) {
                known = router;
                if (known == null) {
                    known = new TransitRouter(load());
                    router = known;
                }
            }
        }
        return known;
    }

    static TransitNetwork load() {
        InputStream stream = TransitService.class.getResourceAsStream(NETWORK_RESOURCE);
        if (stream == null) {
            throw new UnavailableException("Toplu taşıma tarifeleri bulunamadı");
        }
        try (stream) {
            long start = System.nanoTime();
            TransitNetwork network = TransitNetworkFile.read(stream);
            LOGGER.info(() -> "public transport read in " + (System.nanoTime() - start) / 1_000_000 + " ms: "
                    + network.stopCount() + " stops, " + network.patternCount() + " patterns");
            return network;
        } catch (IOException e) {
            throw new UncheckedIOException("the bundled public transport timetables cannot be read", e);
        }
    }
}
