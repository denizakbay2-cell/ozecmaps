package com.citynavigator.external;

import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.Instruction;
import com.citynavigator.routing.Maneuver;
import com.citynavigator.routing.TravelMode;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Routes from an OSRM server (by default the FOSSGIS servers behind the directions on openstreetmap.org), turned
 * into the application's own route and instruction types.
 */
public final class OsrmClient {

    /** {@code {profile}} becomes car, bike or foot. */
    public static final String FOSSGIS = "https://routing.openstreetmap.de/routed-{profile}/route/v1/driving/";

    private final ExternalHttp http;
    private final String urlTemplate;
    private final MinIntervalLimiter limiter;
    private final TtlCache<String, List<ExternalRoute>> cache;

    public OsrmClient(ExternalHttp http, String urlTemplate, Clock clock) {
        this.http = http;
        this.urlTemplate = urlTemplate;
        // FOSSGIS policy: at most one request per second
        this.limiter = new MinIntervalLimiter(Duration.ofMillis(1100), Duration.ofSeconds(4));
        this.cache = new TtlCache<>(200, Duration.ofMinutes(15), clock);
    }

    /**
     * The fastest route between the points, followed by up to {@code alternatives} clearly different ones if the
     * service finds them; empty if it finds no route at all.
     */
    public List<ExternalRoute> routes(GeoPoint from, GeoPoint to, TravelMode mode, int alternatives) {
        URI uri = uri(from, to, mode, alternatives);
        return cache.computeIfAbsent(uri.toString(), () -> {
            limiter.acquire();
            ExternalHttp.JsonResponse response = http.get(uri);
            return parse(response.body());
        });
    }

    URI uri(GeoPoint from, GeoPoint to, TravelMode mode, int alternatives) {
        String profile = switch (mode) {
            case CAR -> "car";
            case BICYCLE -> "bike";
            case FOOT -> "foot";
        };
        return URI.create(urlTemplate.replace("{profile}", profile) + lonLat(from) + ";" + lonLat(to)
                + "?overview=false&geometries=polyline6&steps=true"
                + (alternatives > 0 ? "&alternatives=" + alternatives : ""));
    }

    private static String lonLat(GeoPoint point) {
        return String.format(Locale.ROOT, "%.6f,%.6f", point.lon(), point.lat());
    }

    static List<ExternalRoute> parse(JsonObject json) {
        String code = json.getString("code", "");
        if ("NoRoute".equals(code) || "NoSegment".equals(code)) {
            return List.of();
        }
        if (!"Ok".equals(code)) {
            throw new ExternalServiceException("the routing service answered " + code + ": "
                    + json.getString("message", ""));
        }
        JsonArray waypoints = json.getJsonArray("waypoints");
        ExternalRoute.Waypoint from = waypoint(waypoints.getJsonObject(0));
        ExternalRoute.Waypoint to = waypoint(waypoints.getJsonObject(waypoints.size() - 1));
        return json.getJsonArray("routes").stream().map(route -> route(route.asJsonObject(), from, to)).toList();
    }

    private static ExternalRoute route(JsonObject route, ExternalRoute.Waypoint from, ExternalRoute.Waypoint to) {
        List<GeoPoint> points = new ArrayList<>();
        List<Instruction> instructions = new ArrayList<>();
        for (JsonValue leg : route.getJsonArray("legs")) {
            for (JsonValue step : leg.asJsonObject().getJsonArray("steps")) {
                addStep(step.asJsonObject(), points, instructions);
            }
        }
        return new ExternalRoute(number(route, "distance"), number(route, "duration"), points, instructions, from, to);
    }

    private static void addStep(JsonObject step, List<GeoPoint> points, List<Instruction> instructions) {
        JsonObject maneuverJson = step.getJsonObject("maneuver");
        List<GeoPoint> stepPoints = Polyline.decode(step.getString("geometry", ""), 6);
        int pointIndex = append(points, stepPoints);
        double distance = number(step, "distance");
        double duration = number(step, "duration");
        Maneuver maneuver = maneuverOf(maneuverJson.getString("type", ""), maneuverJson.getString("modifier", ""));
        if (maneuver == null) {
            // a step that announces nothing new: its stretch belongs to the instruction before it
            if (!instructions.isEmpty()) {
                Instruction previous = instructions.removeLast();
                instructions.add(new Instruction(previous.maneuver(), previous.street(), previous.roadClass(),
                        previous.exitNumber(), previous.pointIndex(), previous.bearing(),
                        previous.distanceMeters() + distance, previous.durationSeconds() + duration));
            }
            return;
        }
        int exit = maneuverJson.containsKey("exit") ? maneuverJson.getInt("exit") : 0;
        double bearing = maneuver == Maneuver.DEPART ? number(maneuverJson, "bearing_after") : 0;
        instructions.add(new Instruction(maneuver, streetOf(step), null, exit, pointIndex, bearing, distance,
                duration));
    }

    /** Adds the step's geometry, which starts where the previous one ended; returns where the step starts. */
    private static int append(List<GeoPoint> points, List<GeoPoint> stepPoints) {
        int start = points.isEmpty() ? 0 : points.size() - 1;
        if (!points.isEmpty() && !stepPoints.isEmpty() && !points.getLast().equals(stepPoints.getFirst())) {
            start = points.size();
        }
        for (GeoPoint point : stepPoints) {
            if (points.isEmpty() || !points.getLast().equals(point)) {
                points.add(point);
            }
        }
        return Math.min(start, Math.max(0, points.size() - 1));
    }

    static Maneuver maneuverOf(String type, String modifier) {
        return switch (type) {
            case "depart" -> Maneuver.DEPART;
            case "arrive" -> Maneuver.ARRIVE;
            case "roundabout", "rotary" -> Maneuver.ROUNDABOUT;
            case "exit roundabout", "exit rotary", "notification", "use lane" -> null;
            default -> switch (modifier) {
                case "uturn" -> Maneuver.U_TURN;
                case "sharp right" -> Maneuver.SHARP_RIGHT;
                case "right" -> Maneuver.RIGHT;
                case "slight right" -> Maneuver.SLIGHT_RIGHT;
                case "slight left" -> Maneuver.SLIGHT_LEFT;
                case "left" -> Maneuver.LEFT;
                case "sharp left" -> Maneuver.SHARP_LEFT;
                default -> Maneuver.CONTINUE;
            };
        };
    }

    private static String streetOf(JsonObject step) {
        String name = step.getString("name", "").strip();
        String ref = step.getString("ref", "").strip();
        if (!name.isEmpty()) {
            return name;
        }
        return ref.isEmpty() ? null : ref;
    }

    private static ExternalRoute.Waypoint waypoint(JsonObject json) {
        JsonArray location = json.getJsonArray("location");
        String name = json.getString("name", "").strip();
        return new ExternalRoute.Waypoint(name.isEmpty() ? null : name,
                new GeoPoint(location.getJsonNumber(1).doubleValue(), location.getJsonNumber(0).doubleValue()),
                number(json, "distance"));
    }

    private static double number(JsonObject json, String key) {
        return json.containsKey(key) && json.get(key).getValueType() == JsonValue.ValueType.NUMBER
                ? json.getJsonNumber(key).doubleValue() : 0;
    }
}
