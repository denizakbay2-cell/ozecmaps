package com.citynavigator.service;

import com.citynavigator.dto.TrafficRoadView;
import com.citynavigator.dto.TrafficStatus;
import com.citynavigator.exception.BusinessRuleException;
import com.citynavigator.external.ExternalServiceException;
import com.citynavigator.external.TrafficIndexClient;
import com.citynavigator.routing.CongestionProfile;
import com.citynavigator.routing.DayTime;
import com.citynavigator.routing.GeoMath;
import com.citynavigator.routing.NationalGraph;
import com.citynavigator.routing.RoadClass;
import com.citynavigator.routing.RoadGraph;
import com.citynavigator.routing.RoadTraffic;
import com.citynavigator.routing.TravelMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

/**
 * Istanbul traffic. The usual congestion of each place by day type and hour comes with the application (built from
 * İBB's hourly traffic density data by {@code BuildTrafficProfile}); İBB's live traffic index tells how much busier
 * or calmer the city is right now than usual, and scales that picture for departures around now.
 */
@ApplicationScoped
public class TrafficService {

    static final String PROFILE_RESOURCE = "/traffic/istanbul-congestion.csv.gz";
    static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    static final String SOURCE = "İBB Açık Veri Portalı: saatlik trafik yoğunluğu ve trafik endeksi (İBB Açık Veri Lisansı)";
    /** The live index describes the present; a departure further from now uses the usual picture only. */
    static final Duration LIVE_WINDOW = Duration.ofMinutes(30);
    /** A reading older than this no longer describes the present. */
    static final Duration MAX_READING_AGE = Duration.ofMinutes(30);
    /** The city-wide index says little about one street, so it may move the usual speeds only this much. */
    static final double MIN_ADJUSTMENT = 0.75;
    static final double MAX_ADJUSTMENT = 1.25;
    /** The traffic layer is for a city view, about 50 km across at most. */
    static final double MAX_VIEW_DEGREES = 0.5;
    /** Above this view size only main roads are drawn. */
    static final double MAIN_ROADS_ONLY_DEGREES = 0.12;
    static final int MAX_ROADS = 2_500;
    /** In a wide view shape points closer than this to the previous one are not sent. */
    static final double WIDE_VIEW_SPACING_METERS = 30;
    /** Main roads looked at for one view; those without traffic data are dropped afterwards. */
    static final int MAX_CANDIDATES = 40_000;

    private static final Logger LOGGER = Logger.getLogger(TrafficService.class.getName());

    private Clock clock;
    private TrafficIndexClient index;
    private ExternalServices external;
    private RoutingEngine engine;
    private NationalEngine national;
    private CongestionProfile profile;

    protected TrafficService() {
        // for the CDI proxy
    }

    @Inject
    public TrafficService(Clock clock, TrafficIndexClient index, ExternalServices external, RoutingEngine engine,
                          NationalEngine national) {
        this(clock, index, external, engine, national, bundledProfile());
    }

    TrafficService(Clock clock, TrafficIndexClient index, ExternalServices external, RoutingEngine engine,
                   NationalEngine national, CongestionProfile profile) {
        this.clock = clock;
        this.index = index;
        this.external = external;
        this.engine = engine;
        this.national = national;
        this.profile = profile;
    }

    /**
     * The congestion a car route leaving at that moment should expect.
     */
    public RoadTraffic forDeparture(Instant departAt) {
        DayTime when = DayTime.of(LocalDateTime.ofInstant(departAt, ISTANBUL));
        double adjustment = 1;
        if (Duration.between(clock.instant(), departAt).abs().compareTo(LIVE_WINDOW) <= 0) {
            adjustment = live().map(Live::adjustment).orElse(1.0);
        }
        return new RoadTraffic(profile, when, adjustment);
    }

    /**
     * How busy the city is now compared to usual.
     */
    public TrafficStatus status() {
        return live().map(live -> new TrafficStatus(live.index(),
                        Double.isNaN(live.typical()) ? null : (double) Math.round(live.typical()),
                        level(live.index()), comparison(live), live.at(), profile.cellCount(), SOURCE))
                .orElseGet(() -> new TrafficStatus(null, null, null, null, null, profile.cellCount(), SOURCE));
    }

    /**
     * Roads with traffic data inside a map view, each with the share of its free-flow speed traffic leaves it now. If
     * there are too many, the most important roads are kept, and of equally important ones those nearest the centre.
     */
    public List<TrafficRoadView> roadsWithin(double minLat, double minLon, double maxLat, double maxLon) {
        if (minLat > maxLat || minLon > maxLon) {
            throw new BusinessRuleException("Görünüm sınırları ters çevrilmiş");
        }
        if (maxLat - minLat > MAX_VIEW_DEGREES || maxLon - minLon > MAX_VIEW_DEGREES * 1.4) {
            throw new BusinessRuleException("Trafiği görmek için haritayı yakınlaştırın");
        }
        RoadTraffic now = forDeparture(clock.instant());
        boolean wide = maxLat - minLat > MAIN_ROADS_ONLY_DEGREES;
        RoadClass lowest = wide ? RoadClass.PRIMARY_LINK : RoadClass.TERTIARY_LINK;
        // about 10 m in a wide view, 1 m in a close one: finer than a screen pixel either way
        double scale = wide ? 1e4 : 1e5;
        double minSpacing = wide ? WIDE_VIEW_SPACING_METERS : 0;
        Optional<NationalEngine.Network> network = national.network();
        List<Road> roads;
        if (network.isPresent()) {
            NationalGraph graph = network.get().graph();
            int[] edges = network.get().index().edgesWithin(minLat, minLon, maxLat, maxLon,
                    edge -> graph.roadClass(edge).ordinal() <= lowest.ordinal(), MAX_CANDIDATES);
            roads = nationalRoads(graph, edges, now, scale, minSpacing);
        } else {
            roads = detailedRoads(engine.snapshot().graph(), minLat, minLon, maxLat, maxLon, now, lowest, scale);
        }
        double centerLat = (minLat + maxLat) / 2;
        double centerLon = (minLon + maxLon) / 2;
        return roads.stream()
                .sorted(Comparator.comparingInt(Road::rank).thenComparingDouble(road -> road.distanceTo(centerLat,
                        centerLon)))
                .limit(MAX_ROADS)
                .map(Road::view)
                .toList();
    }

    public CongestionProfile profile() {
        return profile;
    }

    private static List<Road> nationalRoads(NationalGraph graph, int[] edges, RoadTraffic now, double scale,
                                            double minSpacing) {
        List<Road> roads = new ArrayList<>();
        for (int edge : edges) {
            RoadClass type = graph.roadClass(edge);
            double lat = (graph.lat(graph.from(edge)) + graph.lat(graph.to(edge))) / 2;
            double lon = (graph.lon(graph.from(edge)) + graph.lon(graph.to(edge))) / 2;
            if (!now.covers(lat, lon)) {
                continue;
            }
            int last = graph.pointCount(edge) - 1;
            List<double[]> points = new ArrayList<>(last + 1);
            for (int i = 0; i <= last; i++) {
                double[] point = {round(graph.pointLat(edge, i), scale), round(graph.pointLon(edge, i), scale)};
                if (!points.isEmpty()) {
                    double[] previous = points.getLast();
                    // shape points closer together than the view can show are left out; the ends always stay
                    boolean tooClose = i < last
                            && GeoMath.distance(previous[0], previous[1], point[0], point[1]) < minSpacing;
                    if (tooClose || Arrays.equals(previous, point)) {
                        continue;
                    }
                }
                points.add(point);
            }
            if (points.size() < 2) {
                continue;
            }
            roads.add(new Road(type.ordinal(), lat, lon, new TrafficRoadView(share(now.factor(lat, lon)), points)));
        }
        return roads;
    }

    /** Without the national graph, the detailed area's own roads carry the layer. */
    private static List<Road> detailedRoads(RoadGraph graph, double minLat, double minLon, double maxLat,
                                            double maxLon, RoadTraffic now, RoadClass lowest, double scale) {
        List<Road> roads = new ArrayList<>();
        for (int segment = 0; segment < graph.segmentCount(); segment++) {
            RoadClass type = graph.way(segment).roadClass();
            int from = graph.from(segment);
            int to = graph.to(segment);
            double lat = (graph.lat(from) + graph.lat(to)) / 2;
            double lon = (graph.lon(from) + graph.lon(to)) / 2;
            if (type.ordinal() > lowest.ordinal() || lat < minLat || lat > maxLat || lon < minLon || lon > maxLon
                    || !graph.allowsAny(segment, TravelMode.CAR) || !now.covers(lat, lon)) {
                continue;
            }
            roads.add(new Road(type.ordinal(), lat, lon, new TrafficRoadView(share(now.factor(lat, lon)), List.of(
                    new double[] {round(graph.lat(from), scale), round(graph.lon(from), scale)},
                    new double[] {round(graph.lat(to), scale), round(graph.lon(to), scale)}))));
        }
        return roads;
    }

    private Optional<Live> live() {
        if (!external.enabled()) {
            return Optional.empty();
        }
        try {
            Optional<TrafficIndexClient.Reading> reading = index.current();
            LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ISTANBUL);
            if (reading.isEmpty() || Duration.between(reading.get().at(), now).compareTo(MAX_READING_AGE) > 0) {
                return Optional.empty();
            }
            TrafficIndexClient.Reading latest = reading.get();
            return Optional.of(new Live(latest.index(), index.typical(DayTime.of(latest.at())), latest.at()));
        } catch (ExternalServiceException e) {
            LOGGER.log(Level.FINE, "the live traffic index is not available", e);
            return Optional.empty();
        }
    }

    static String level(int index) {
        if (index < 25) {
            return "akıcı";
        }
        if (index < 45) {
            return "normal";
        }
        return index < 65 ? "yoğun" : "çok yoğun";
    }

    static String comparison(Live live) {
        if (Double.isNaN(live.typical())) {
            return null;
        }
        double difference = live.index() - live.typical();
        if (difference > 8) {
            return "normalden yoğun";
        }
        return difference < -8 ? "normalden akıcı" : "her zamanki gibi";
    }

    static CongestionProfile bundledProfile() {
        try (InputStream resource = TrafficService.class.getResourceAsStream(PROFILE_RESOURCE)) {
            if (resource == null) {
                LOGGER.warning(() -> PROFILE_RESOURCE + " is missing: routes assume free-flowing traffic");
                return CongestionProfile.NONE;
            }
            try (InputStream input = new GZIPInputStream(resource)) {
                CongestionProfile loaded = CongestionProfile.read(input);
                LOGGER.info(() -> "Istanbul congestion profile: " + loaded.cellCount() + " cells");
                return loaded;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + PROFILE_RESOURCE, e);
        }
    }

    private static double share(double factor) {
        return Math.round(factor * 100) / 100.0;
    }

    private static double round(double coordinate, double scale) {
        return Math.round(coordinate * scale) / scale;
    }

    /**
     * The live index next to its usual value at that day type and hour.
     *
     * @param typical {@code NaN} when unknown
     */
    record Live(int index, double typical, LocalDateTime at) {

        /**
         * How much faster (above 1) or slower (below 1) traffic moves than usual. The index grows with the length
         * of congested roads, so {@code 101 - index} stands for the free part of the network; the square root of
         * the ratio damps the effect, because one city-wide number says little about a single street.
         */
        double adjustment() {
            if (Double.isNaN(typical)) {
                return 1;
            }
            return Math.clamp(Math.sqrt((101.0 - index) / (101.0 - typical)), MIN_ADJUSTMENT, MAX_ADJUSTMENT);
        }
    }

    /**
     * @param rank the road class; lower is more important
     * @param lat  the middle of the road, with {@code lon}
     */
    private record Road(int rank, double lat, double lon, TrafficRoadView view) {

        /** Squared distance in degrees, which is enough to order roads by nearness. */
        double distanceTo(double centerLat, double centerLon) {
            double dLat = lat - centerLat;
            double dLon = (lon - centerLon) * Math.cos(Math.toRadians(centerLat));
            return dLat * dLat + dLon * dLon;
        }
    }
}
