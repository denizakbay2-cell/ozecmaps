package com.citynavigator.external;

import com.citynavigator.routing.GeoPoint;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Searches places and businesses all over Turkey, and names points, with the Photon geocoder (OpenStreetMap data,
 * by komoot).
 */
public final class PhotonClient {

    public static final String KOMOOT = "https://photon.komoot.io";
    /** minLon,minLat,maxLon,maxLat of Turkey, so a name that also exists abroad stays at home. */
    static final String TURKEY = "25.6,35.8,44.9,42.2";

    private final ExternalHttp http;
    private final String baseUrl;
    private final MinIntervalLimiter limiter;
    private final TtlCache<String, List<GeocodedPlace>> cache;

    public PhotonClient(ExternalHttp http, String baseUrl, Clock clock) {
        this.http = http;
        this.baseUrl = baseUrl;
        this.limiter = new MinIntervalLimiter(Duration.ofMillis(250), Duration.ofSeconds(3));
        this.cache = new TtlCache<>(500, Duration.ofHours(1), clock);
    }

    /**
     * Places whose name matches the text, the ones near {@code near} (if given) first.
     */
    public List<GeocodedPlace> search(String text, GeoPoint near, int limit) {
        return fetch(uri(text, near, limit));
    }

    /**
     * What is at a point: the nearest named object, an address or a road.
     */
    public Optional<GeocodedPlace> reverse(GeoPoint point) {
        return fetch(URI.create(baseUrl + String.format(Locale.ROOT, "/reverse?lat=%.5f&lon=%.5f&limit=1&lang=default",
                point.lat(), point.lon()))).stream().findFirst();
    }

    private List<GeocodedPlace> fetch(URI uri) {
        return cache.computeIfAbsent(uri.toString(), () -> {
            limiter.acquire();
            ExternalHttp.JsonResponse response = http.get(uri);
            if (response.status() != 200) {
                throw new ExternalServiceException("the geocoder answered HTTP " + response.status());
            }
            return parse(response.body());
        });
    }

    URI uri(String text, GeoPoint near, int limit) {
        StringBuilder query = new StringBuilder(baseUrl)
                .append("/api/?q=").append(URLEncoder.encode(text.strip(), StandardCharsets.UTF_8))
                .append("&limit=").append(limit)
                .append("&lang=default&bbox=").append(TURKEY);
        if (near != null) {
            // two decimals (about a kilometre) is enough to rank by nearness and lets nearby searches share a cache
            query.append(String.format(Locale.ROOT, "&lat=%.2f&lon=%.2f", near.lat(), near.lon()));
        }
        return URI.create(query.toString());
    }

    static List<GeocodedPlace> parse(JsonObject json) {
        List<GeocodedPlace> places = new ArrayList<>();
        for (JsonValue value : json.getJsonArray("features")) {
            JsonObject feature = value.asJsonObject();
            JsonObject properties = feature.getJsonObject("properties");
            JsonArray coordinates = feature.getJsonObject("geometry").getJsonArray("coordinates");
            String street = text(properties, "street");
            String name = text(properties, "name");
            if (name == null && street != null) {
                String number = text(properties, "housenumber");
                name = number == null ? street : street + " No: " + number;
            }
            if (name == null) {
                continue;
            }
            places.add(new GeocodedPlace(name, osmRef(properties), text(properties, "osm_key"),
                    text(properties, "osm_value"), coordinates.getJsonNumber(1).doubleValue(),
                    coordinates.getJsonNumber(0).doubleValue(), detail(properties, name)));
        }
        return places;
    }

    private static String osmRef(JsonObject properties) {
        String type = switch (properties.getString("osm_type", "")) {
            case "N" -> "node";
            case "W" -> "way";
            case "R" -> "relation";
            default -> null;
        };
        return type == null || !properties.containsKey("osm_id") ? null
                : type + "/" + properties.getJsonNumber("osm_id").longValue();
    }

    /**
     * "Street No: 12, neighbourhood, district, city, province", leaving out parts that repeat the name or each other:
     * enough to tell two branches of a chain apart.
     */
    private static String detail(JsonObject properties, String name) {
        Set<String> parts = new LinkedHashSet<>();
        String street = text(properties, "street");
        String number = text(properties, "housenumber");
        if (street != null && !name.startsWith(street)) {
            parts.add(number == null ? street : street + " No: " + number);
        }
        for (String key : new String[] {"locality", "district", "city", "county", "state"}) {
            String part = text(properties, key);
            if (part != null && !part.equals(name) && !name.startsWith(part)) {
                parts.add(part);
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String text(JsonObject properties, String key) {
        if (!properties.containsKey(key) || properties.get(key).getValueType() != JsonValue.ValueType.STRING) {
            return null;
        }
        String value = properties.getString(key).strip();
        return value.isEmpty() ? null : value;
    }
}
