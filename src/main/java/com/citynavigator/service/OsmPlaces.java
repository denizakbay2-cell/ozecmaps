package com.citynavigator.service;

import com.citynavigator.entity.Place;
import com.citynavigator.entity.PlaceCategory;
import com.citynavigator.entity.PlaceDetails;
import com.citynavigator.osm.OverpassReader;
import com.citynavigator.osm.RoadExtract;
import com.citynavigator.routing.GeoMath;
import com.citynavigator.routing.RoadWay;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Searchable places from OpenStreetMap: tagged landmarks, businesses with their details, and one entry per street.
 */
final class OsmPlaces {

    /** The same name in the same category closer than this is one place (a station mapped as node and area). */
    static final double DUPLICATE_METERS = 300;
    /** Chains open branches a street apart, so two businesses are only the same one when practically on top. */
    static final double DUPLICATE_BUSINESS_METERS = 25;

    private static final Set<String> SETTLEMENTS = Set.of("city", "town", "village", "hamlet", "municipality",
            "county", "district", "province", "state");
    private static final Set<String> NEIGHBOURHOODS = Set.of("suburb", "neighbourhood", "quarter");
    private static final Map<String, PlaceCategory> AMENITIES = Map.ofEntries(
            Map.entry("ferry_terminal", PlaceCategory.TRANSIT), Map.entry("bus_station", PlaceCategory.TRANSIT),
            Map.entry("hospital", PlaceCategory.HEALTH), Map.entry("clinic", PlaceCategory.HEALTH),
            Map.entry("pharmacy", PlaceCategory.HEALTH), Map.entry("school", PlaceCategory.EDUCATION),
            Map.entry("university", PlaceCategory.EDUCATION), Map.entry("college", PlaceCategory.EDUCATION),
            Map.entry("library", PlaceCategory.EDUCATION), Map.entry("theatre", PlaceCategory.CULTURE),
            Map.entry("cinema", PlaceCategory.CULTURE), Map.entry("arts_centre", PlaceCategory.CULTURE),
            Map.entry("marketplace", PlaceCategory.SHOPPING), Map.entry("townhall", PlaceCategory.PUBLIC_SERVICE),
            Map.entry("courthouse", PlaceCategory.PUBLIC_SERVICE), Map.entry("police", PlaceCategory.PUBLIC_SERVICE),
            Map.entry("fire_station", PlaceCategory.PUBLIC_SERVICE),
            Map.entry("post_office", PlaceCategory.PUBLIC_SERVICE),
            Map.entry("community_centre", PlaceCategory.PUBLIC_SERVICE),
            Map.entry("conference_centre", PlaceCategory.PUBLIC_SERVICE),
            Map.entry("place_of_worship", PlaceCategory.WORSHIP), Map.entry("fuel", PlaceCategory.FUEL));
    private static final Map<String, PlaceCategory> TOURISM = Map.of("museum", PlaceCategory.CULTURE,
            "gallery", PlaceCategory.CULTURE, "attraction", PlaceCategory.CULTURE, "viewpoint", PlaceCategory.PARK,
            "hotel", PlaceCategory.HOTEL);
    private static final Set<String> LEISURE = Set.of("park", "stadium", "sports_centre", "marina");
    private static final Set<String> SHOPS = Set.of("mall", "department_store");
    private static final Set<String> STATIONS = Set.of("station", "halt");

    private OsmPlaces() {
    }

    static List<Place> read(InputStream json, Instant now) {
        List<Place> places = new ArrayList<>();
        OverpassReader.read(json, element -> {
            String name = element.tags().get("name");
            if (name == null || name.isBlank() || name.length() > Place.NAME_MAX_LENGTH || !element.hasPosition()) {
                return;
            }
            categoryOf(element.tags()).ifPresent(category -> places.add(new Place(name, category, element.lat(),
                    element.lon(), element.type() + "/" + element.id(), now)));
        });
        return places;
    }

    /**
     * Named businesses with a position, with their kind, opening hours and contact details.
     */
    static List<Place> readBusinesses(InputStream json, Instant now) {
        List<Place> businesses = new ArrayList<>();
        OverpassReader.read(json, element -> {
            String name = element.tags().get("name");
            if (name == null || name.isBlank() || name.length() > Place.NAME_MAX_LENGTH || !element.hasPosition()) {
                return;
            }
            OsmKinds.businessOf(element.tags()).ifPresent(kind -> businesses.add(
                    new Place(name, kind.category(), element.lat(), element.lon(), element.type() + "/" + element.id(),
                            now).asBusiness(detailsOf(element.tags(), kind.label()))));
        });
        return businesses;
    }

    static PlaceDetails detailsOf(Map<String, String> tags, String kind) {
        return new PlaceDetails(kind, tags.get("opening_hours"),
                tags.getOrDefault("phone", tags.get("contact:phone")),
                tags.getOrDefault("website", tags.get("contact:website")));
    }

    static Optional<PlaceCategory> categoryOf(Map<String, String> tags) {
        String place = tags.getOrDefault("place", "");
        if (SETTLEMENTS.contains(place)) {
            return Optional.of(PlaceCategory.SETTLEMENT);
        }
        if (NEIGHBOURHOODS.contains(place)) {
            return Optional.of(PlaceCategory.NEIGHBOURHOOD);
        }
        PlaceCategory amenity = AMENITIES.get(tags.getOrDefault("amenity", ""));
        if (amenity != null) {
            return Optional.of(amenity);
        }
        if (STATIONS.contains(tags.getOrDefault("railway", "")) || "station".equals(tags.get("public_transport"))) {
            return Optional.of(PlaceCategory.TRANSIT);
        }
        PlaceCategory tourism = TOURISM.get(tags.getOrDefault("tourism", ""));
        if (tourism != null) {
            return Optional.of(tourism);
        }
        if (LEISURE.contains(tags.getOrDefault("leisure", ""))) {
            return Optional.of(PlaceCategory.PARK);
        }
        if (SHOPS.contains(tags.getOrDefault("shop", ""))) {
            return Optional.of(PlaceCategory.SHOPPING);
        }
        return tags.containsKey("historic") ? Optional.of(PlaceCategory.CULTURE) : Optional.empty();
    }

    /**
     * One place per street name, at the middle node of the longest way carrying that name.
     */
    static List<Place> streets(RoadExtract roads, Instant now) {
        Map<String, RoadExtract.Way> longest = new LinkedHashMap<>();
        for (RoadExtract.Way way : roads.ways()) {
            RoadWay road = way.way();
            if (road.hasName() && road.name().length() <= Place.NAME_MAX_LENGTH) {
                longest.merge(road.name(), way, (kept, other) -> other.nodes().size() > kept.nodes().size() ? other : kept);
            }
        }
        Map<Long, RoadExtract.Node> nodes = new HashMap<>();
        roads.nodes().forEach(node -> nodes.put(node.id(), node));
        List<Place> streets = new ArrayList<>(longest.size());
        longest.forEach((name, way) -> {
            RoadExtract.Node middle = nodes.get(way.nodes().get(way.nodes().size() / 2));
            streets.add(new Place(name, PlaceCategory.STREET, middle.lat(), middle.lon(), "way/" + way.way().id(), now));
        });
        return streets;
    }

    /**
     * Drops repeated OpenStreetMap objects and the same name in the same category close to one already kept.
     * Whatever comes first wins, so businesses are passed before landmarks that may repeat them.
     */
    static List<Place> withoutDuplicates(List<Place> places) {
        Set<String> refs = new HashSet<>();
        Map<String, List<Place>> keptByName = new HashMap<>();
        List<Place> kept = new ArrayList<>();
        for (Place place : places) {
            if (place.getOsmRef() != null && !refs.add(place.getOsmRef())) {
                continue;
            }
            List<Place> sameName = keptByName.computeIfAbsent(
                    place.getCategory() + ":" + place.getSearchKey(), key -> new ArrayList<>());
            double radius = place.isBusiness() ? DUPLICATE_BUSINESS_METERS : DUPLICATE_METERS;
            boolean nearby = sameName.stream().anyMatch(other -> GeoMath.distance(other.getLat(), other.getLon(),
                    place.getLat(), place.getLon()) < radius);
            if (!nearby) {
                sameName.add(place);
                kept.add(place);
            }
        }
        return kept;
    }
}
