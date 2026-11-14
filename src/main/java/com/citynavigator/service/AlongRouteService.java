package com.citynavigator.service;

import com.citynavigator.dto.AlongPlaceView;
import com.citynavigator.exception.BusinessRuleException;
import com.citynavigator.exception.UnavailableException;
import com.citynavigator.external.Polyline;
import com.citynavigator.routing.GeoMath;
import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.RoadsidePlace;
import com.citynavigator.routing.RoadsidePlaces;
import com.citynavigator.routing.TravelMode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

/**
 * Finds fuel and charging stations, pharmacies, banks and markets along a planned route, so that one can be added as a
 * stop on the way. The places are those of all of Turkey, taken from OpenStreetMap by the {@code BuildPoiIndex} tool
 * and shipped with the application (see ADR-0012); inside the detailed area each also gets the street it is on.
 */
@ApplicationScoped
public class AlongRouteService {

    static final String PLACES_RESOURCE = "/pois/turkey-pois.csv.gz";
    /** The most places one search returns, spread over the route. */
    static final int LIMIT = 20;
    /** The longest line taken, in points; the application sends at most 250. */
    static final int MAX_POINTS = 2_000;
    /** A named road this close to a place is the street it is on. */
    static final double STREET_METERS = 80;
    /** How far off the route a place may lie (m): a driver goes further for fuel than a walker for a pharmacy. */
    static final Map<TravelMode, Double> CORRIDOR_METERS = Map.of(
            TravelMode.CAR, 500.0, TravelMode.BICYCLE, 300.0, TravelMode.FOOT, 200.0);
    static final Map<RoadsidePlace.Kind, String> KIND_LABELS = Map.of(
            RoadsidePlace.Kind.FUEL, "Akaryakıt istasyonu",
            RoadsidePlace.Kind.CHARGING, "Şarj istasyonu",
            RoadsidePlace.Kind.PHARMACY, "Eczane",
            RoadsidePlace.Kind.MONEY, "Banka / ATM",
            RoadsidePlace.Kind.MARKET, "Market");

    private static final Logger LOGGER = Logger.getLogger(AlongRouteService.class.getName());

    private RoutingEngine engine;
    /** Read on first use; about 75 000 places. */
    private volatile RoadsidePlaces places;

    protected AlongRouteService() {
        // for the CDI proxy
    }

    @Inject
    public AlongRouteService(RoutingEngine engine) {
        this.engine = engine;
    }

    /** For tests: searches the given places instead of the bundled list. */
    AlongRouteService(RoutingEngine engine, RoadsidePlaces places) {
        this.engine = engine;
        this.places = places;
    }

    /**
     * The places of a kind along the route, in the order the route passes them.
     *
     * @param encodedLine the route as an encoded polyline (Google's format, five decimals)
     */
    public List<AlongPlaceView> along(RoadsidePlace.Kind kind, TravelMode mode, String encodedLine) {
        List<GeoPoint> line = decode(encodedLine);
        double length = 0;
        for (int i = 0; i + 1 < line.size(); i++) {
            length += GeoMath.distance(line.get(i), line.get(i + 1));
        }
        List<RoadsidePlaces.Found> found = RoadsidePlaces.spread(
                places().along(line, kind, CORRIDOR_METERS.get(mode)), length, LIMIT);
        RoutingEngine.Snapshot snapshot = engine.snapshot();
        return found.stream().map(place -> view(place, snapshot)).toList();
    }

    private static List<GeoPoint> decode(String encodedLine) {
        List<GeoPoint> line;
        try {
            line = Polyline.decode(encodedLine, 5);
        } catch (IllegalArgumentException e) {
            throw new BusinessRuleException("Rota çizgisi okunamadı");
        }
        if (line.size() < 2) {
            throw new BusinessRuleException("Rota çizgisinde en az iki nokta olmalı");
        }
        if (line.size() > MAX_POINTS) {
            throw new BusinessRuleException("Rota çizgisinde en fazla " + MAX_POINTS + " nokta olabilir");
        }
        return line;
    }

    private static AlongPlaceView view(RoadsidePlaces.Found found, RoutingEngine.Snapshot snapshot) {
        RoadsidePlace place = found.place();
        return new AlongPlaceView(place.name(), KIND_LABELS.get(place.kind()), streetOf(place, snapshot),
                place.lat(), place.lon(), Math.round(found.alongMeters()), Math.round(found.offsetMeters()),
                found.onTheRight() ? "RIGHT" : "LEFT");
    }

    /** Inside the detailed area, the named road the place is on, unless the place is named after it. */
    private static String streetOf(RoadsidePlace place, RoutingEngine.Snapshot snapshot) {
        GeoPoint point = new GeoPoint(place.lat(), place.lon());
        if (!snapshot.graph().covers(point, 0)) {
            return null;
        }
        return snapshot.index().nearest(point, STREET_METERS, segment -> snapshot.graph().way(segment).hasName())
                .map(snap -> snapshot.graph().way(snap.segment()).name())
                .filter(street -> !street.equals(place.name()))
                .orElse(null);
    }

    private RoadsidePlaces places() {
        RoadsidePlaces known = places;
        if (known == null) {
            synchronized (this) {
                known = places;
                if (known == null) {
                    known = load();
                    places = known;
                }
            }
        }
        return known;
    }

    static RoadsidePlaces load() {
        InputStream stream = AlongRouteService.class.getResourceAsStream(PLACES_RESOURCE);
        if (stream == null) {
            throw new UnavailableException("Yol üstündeki yerlerin listesi bulunamadı");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new GZIPInputStream(stream),
                StandardCharsets.UTF_8))) {
            RoadsidePlaces loaded = RoadsidePlaces.read(reader);
            LOGGER.info(() -> loaded.size() + " places along routes read");
            return loaded;
        } catch (IOException e) {
            throw new UncheckedIOException("the bundled list of places along routes cannot be read", e);
        }
    }
}
