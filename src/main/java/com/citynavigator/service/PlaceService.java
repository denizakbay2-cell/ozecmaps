package com.citynavigator.service;

import com.citynavigator.dto.PlaceRequest;
import com.citynavigator.dto.PlaceView;
import com.citynavigator.dto.StreetView;
import com.citynavigator.entity.Place;
import com.citynavigator.entity.PlaceCategory;
import com.citynavigator.entity.SearchKey;
import com.citynavigator.exception.BusinessRuleException;
import com.citynavigator.exception.ConflictException;
import com.citynavigator.exception.ResourceNotFoundException;
import com.citynavigator.exception.UnavailableException;
import com.citynavigator.external.ExternalServiceException;
import com.citynavigator.external.PhotonClient;
import com.citynavigator.repository.PlaceRepository;
import com.citynavigator.routing.GeoMath;
import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.Snap;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Searching places and businesses (the application's own data plus, when enabled, the nationwide geocoder), the
 * businesses inside a map view, the street at a point, and places added by hand.
 */
@ApplicationScoped
@Transactional
public class PlaceService {

    static final int MIN_QUERY_LENGTH = 2;
    static final int MAX_WORDS = 5;
    static final int CANDIDATES = 300;
    static final double STREET_RADIUS_METERS = 150;
    static final double DUPLICATE_RADIUS_METERS = 100;
    /** Searching from closer than this to the detailed area puts its own places first. */
    static final double NEAR_AREA_METERS = 3_000;
    /** A nationwide result this close to an own place with the same name is the same place. */
    static final double SAME_PLACE_METERS = 150;
    /** The business layer is for a street-level view, about 3 km across at most. */
    static final double MAX_VIEW_DEGREES = 0.03;
    static final int MAX_BUSINESSES = 400;
    /** A named road this close to a place is the street it is on. */
    static final double ADDRESS_STREET_METERS = 80;
    /** The nearest neighbourhood centre within this distance names the neighbourhood a place is in. */
    static final double ADDRESS_AREA_METERS = 2_000;

    private static final Logger LOGGER = Logger.getLogger(PlaceService.class.getName());

    private PlaceRepository places;
    private RoutingEngine engine;
    private Clock clock;
    private PhotonClient nationwide;
    private ExternalServices external;
    /** Neighbourhood names and centres, read once; places added by hand clear it. */
    private volatile List<Area> areas;

    protected PlaceService() {
        // for the CDI proxy
    }

    @Inject
    public PlaceService(PlaceRepository places, RoutingEngine engine, Clock clock, PhotonClient nationwide,
                        ExternalServices external) {
        this.places = places;
        this.engine = engine;
        this.clock = clock;
        this.nationwide = nationwide;
        this.external = external;
    }

    /**
     * Places with a word starting with every typed word, each with the street and neighbourhood it lies in. Near the
     * detailed area its own places come first (names that start with the whole query, then neighbourhoods and piers
     * before pharmacies, then the nearest, then shorter names); further away the nationwide results, which the
     * geocoder ranks by nearness, come first.
     *
     * @param near where the user is looking (map centre or own position), or {@code null}
     */
    public List<PlaceView> search(String text, GeoPoint near, int limit) {
        String key = SearchKey.of(text);
        if (key.length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        List<String> words = Arrays.stream(key.split(" ")).limit(MAX_WORDS).toList();
        List<Place> own = places.findMatching(words, CANDIDATES).stream().sorted(ranking(key, near)).toList();
        RoutingEngine.Snapshot snapshot = engine.snapshot();
        if (!external.enabled()) {
            return own.stream().limit(limit).map(place -> describe(place, snapshot)).toList();
        }
        boolean nearArea = near == null || snapshot.graph().covers(near, NEAR_AREA_METERS);
        List<PlaceView> remote = remoteSearch(text, near, limit);
        List<PlaceView> result = new ArrayList<>();
        if (nearArea) {
            own.stream().limit(limit).map(place -> describe(place, snapshot)).forEach(result::add);
            addNew(result, remote, limit);
        } else {
            addNew(result, remote, limit);
            // far from the area, only own places that clearly are what was asked for
            addNew(result, own.stream().filter(place -> place.getSearchKey().startsWith(key)).limit(limit)
                    .map(place -> describe(place, snapshot)).toList(), limit);
        }
        return result;
    }

    /**
     * The view of an own place with where it lies: the street it is on and its neighbourhood, for example
     * "Bahariye Caddesi, Osmanağa". The many branches of a chain all share a name; this tells them apart.
     */
    private PlaceView describe(Place place, RoutingEngine.Snapshot snapshot) {
        return DtoMapper.toView(place, addressOf(place, snapshot));
    }

    String addressOf(Place place, RoutingEngine.Snapshot snapshot) {
        PlaceCategory category = place.getCategory();
        if (category == PlaceCategory.NEIGHBOURHOOD || category == PlaceCategory.SETTLEMENT) {
            return null;
        }
        GeoPoint point = new GeoPoint(place.getLat(), place.getLon());
        Set<String> parts = new LinkedHashSet<>();
        if (category != PlaceCategory.STREET) {
            snapshot.index().nearest(point, ADDRESS_STREET_METERS, segment -> snapshot.graph().way(segment).hasName())
                    .map(snap -> snapshot.graph().way(snap.segment()).name())
                    .filter(street -> !street.equals(place.getName()))
                    .ifPresent(parts::add);
        }
        nearestArea(point).filter(area -> !place.getName().startsWith(area)).ifPresent(parts::add);
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private Optional<String> nearestArea(GeoPoint point) {
        Area nearest = null;
        double nearestMeters = ADDRESS_AREA_METERS;
        for (Area area : areas()) {
            double meters = GeoMath.distance(point.lat(), point.lon(), area.lat(), area.lon());
            if (meters < nearestMeters) {
                nearest = area;
                nearestMeters = meters;
            }
        }
        return Optional.ofNullable(nearest).map(Area::name);
    }

    private List<Area> areas() {
        List<Area> known = areas;
        if (known == null) {
            known = places.findInCategories(List.of(PlaceCategory.NEIGHBOURHOOD)).stream()
                    .map(place -> new Area(place.getName(), place.getLat(), place.getLon()))
                    .toList();
            areas = known;
        }
        return known;
    }

    private List<PlaceView> remoteSearch(String text, GeoPoint near, int limit) {
        try {
            return nationwide.search(text, near, limit).stream().map(DtoMapper::toView).toList();
        } catch (ExternalServiceException e) {
            // the own results are still worth showing
            LOGGER.log(Level.WARNING, "the nationwide search failed", e);
            return List.of();
        }
    }

    private static void addNew(List<PlaceView> result, List<PlaceView> candidates, int limit) {
        for (PlaceView candidate : candidates) {
            if (result.size() >= limit) {
                return;
            }
            String key = SearchKey.of(candidate.name());
            boolean known = result.stream().anyMatch(kept -> SearchKey.of(kept.name()).equals(key)
                    && GeoMath.distance(kept.lat(), kept.lon(), candidate.lat(), candidate.lon()) < SAME_PLACE_METERS);
            if (!known) {
                result.add(candidate);
            }
        }
    }

    /**
     * The businesses inside a street-level map view of the detailed area.
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public List<PlaceView> businessesWithin(double minLat, double minLon, double maxLat, double maxLon) {
        if (minLat > maxLat || minLon > maxLon) {
            throw new BusinessRuleException("Görünüm sınırları ters çevrilmiş");
        }
        if (maxLat - minLat > MAX_VIEW_DEGREES || maxLon - minLon > MAX_VIEW_DEGREES * 1.4) {
            throw new BusinessRuleException("İşletmeleri görmek için haritayı yakınlaştırın");
        }
        RoutingEngine.Snapshot snapshot = engine.snapshot();
        return places.findBusinessesWithin(minLat, minLon, maxLat, maxLon, MAX_BUSINESSES).stream()
                .map(place -> describe(place, snapshot))
                .toList();
    }

    public PlaceView get(long id) {
        return describe(find(id), engine.snapshot());
    }

    /**
     * Inside the detailed area the nearest named road within 150 m, or failing that the nearest road of any kind;
     * elsewhere in Turkey whatever the geocoder finds at the point (an address, a place, a road).
     */
    @Transactional(Transactional.TxType.SUPPORTS)
    public StreetView streetAt(GeoPoint point) {
        RoutingEngine.Snapshot snapshot = engine.snapshot();
        if (!snapshot.graph().covers(point, RouteService.AREA_MARGIN_METERS) && external.enabled()) {
            return nameFromGeocoder(point);
        }
        Optional<Snap> named = snapshot.index().nearest(point, STREET_RADIUS_METERS,
                segment -> snapshot.graph().way(segment).hasName());
        Snap snap = named.or(() -> snapshot.index().nearest(point, STREET_RADIUS_METERS, segment -> true))
                .orElseThrow(() -> new BusinessRuleException("Bu noktanın yakınında yol yok"));
        return new StreetView(snapshot.graph().way(snap.segment()).name(), DtoMapper.round(snap.point().lat()),
                DtoMapper.round(snap.point().lon()), Math.round(snap.distanceMeters()));
    }

    private StreetView nameFromGeocoder(GeoPoint point) {
        try {
            return nationwide.reverse(point)
                    .map(place -> new StreetView(place.name(), DtoMapper.round(place.lat()), DtoMapper.round(place.lon()),
                            Math.round(GeoMath.distance(point.lat(), point.lon(), place.lat(), place.lon()))))
                    .orElseThrow(() -> new BusinessRuleException("Bu noktanın yakınında yol yok"));
        } catch (ExternalServiceException e) {
            LOGGER.log(Level.WARNING, "naming a point with the geocoder failed", e);
            throw new UnavailableException("Adres servisine şu an ulaşılamıyor");
        }
    }

    public PlaceView add(PlaceRequest request) {
        GeoPoint point = new GeoPoint(request.lat(), request.lon());
        if (!engine.snapshot().graph().covers(point, RouteService.AREA_MARGIN_METERS)) {
            throw new BusinessRuleException("Yer harita alanının içinde olmalı");
        }
        Place place = new Place(request.name(), PlaceCategory.valueOf(request.category()), point.lat(), point.lon(),
                null, clock.instant());
        double latDegrees = DUPLICATE_RADIUS_METERS / GeoMath.METERS_PER_DEGREE;
        double lonDegrees = latDegrees / Math.cos(Math.toRadians(point.lat()));
        if (places.existsNear(place.getSearchKey(), point.lat(), point.lon(), latDegrees, lonDegrees)) {
            throw new ConflictException("Bu adla bir yer bu noktanın " + (int) DUPLICATE_RADIUS_METERS
                    + " m yakınında zaten var");
        }
        areas = null;
        return describe(places.add(place), engine.snapshot());
    }

    /**
     * Removes a place added by hand; places imported from OpenStreetMap come back with every import, so they
     * cannot be deleted here.
     */
    public void delete(long id) {
        Place place = find(id);
        if (place.isFromOpenStreetMap()) {
            throw new BusinessRuleException("OpenStreetMap'ten gelen yerler silinemez");
        }
        places.remove(place);
        areas = null;
    }

    private Place find(long id) {
        return places.findById(id).orElseThrow(() -> new ResourceNotFoundException("Yer", id));
    }

    static Comparator<Place> ranking(String key) {
        return ranking(key, null);
    }

    /**
     * Names starting with the query first, then by kind (neighbourhoods and piers before shops), then the nearest
     * to where the user looks (so the closest branch of a chain comes first), then shorter names.
     */
    static Comparator<Place> ranking(String key, GeoPoint near) {
        Comparator<Place> order = Comparator.<Place>comparingInt(place -> place.getSearchKey().startsWith(key) ? 0 : 1)
                .thenComparing(Place::getCategory);
        if (near != null) {
            order = order.thenComparingDouble(place -> GeoMath.distance(near.lat(), near.lon(), place.getLat(),
                    place.getLon()));
        }
        return order.thenComparingInt(place -> place.getName().length()).thenComparing(Place::getName);
    }

    private record Area(String name, double lat, double lon) {
    }
}
