package com.citynavigator.osm;

import com.citynavigator.routing.RoadClass;
import com.citynavigator.routing.RoadWay;
import com.citynavigator.routing.TravelMode;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Interprets the tags of an OpenStreetMap way: which travel modes may use it in which direction, its name and
 * speed limit. Follows the common tagging conventions (access hierarchy, {@code oneway}, implied one-way
 * roundabouts and motorways, contraflow cycling) without trying to cover every regional variant.
 */
public final class RoadTags {

    static final int MAX_NAME_LENGTH = 200;

    private static final Set<String> GRANTED = Set.of("yes", "designated", "permissive", "destination", "customers",
            "official", "dismount");
    private static final Set<String> DENIED = Set.of("no", "private", "agricultural", "forestry", "emergency",
            "military", "delivery", "use_sidepath");
    private static final Map<String, Integer> IMPLICIT_LIMITS = Map.of("TR:urban", 50, "TR:rural", 90,
            "TR:trunk", 110, "TR:motorway", 120, "walk", 5);
    private static final Pattern LEADING_NUMBER = Pattern.compile("^(\\d{1,3})(\\s*mph)?");

    private RoadTags() {
    }

    /**
     * Routing attributes of the way, or empty if it is not a road or nobody may travel on it.
     */
    public static Optional<RoadWay> interpret(long wayId, Map<String, String> tags) {
        Optional<RoadClass> roadClass = RoadClass.fromOsm(tags.get("highway"));
        if (roadClass.isEmpty() || "yes".equals(tags.get("area"))) {
            return Optional.empty();
        }
        RoadClass type = roadClass.get();
        boolean openToAll = !DENIED.contains(tags.getOrDefault("access", ""));
        boolean car = permitted(tags, openToAll && type.speedKmh(TravelMode.CAR) > 0,
                "vehicle", "motor_vehicle", "motorcar");
        boolean bicycle = permitted(tags, openToAll && type.speedKmh(TravelMode.BICYCLE) > 0, "vehicle", "bicycle");
        boolean foot = permitted(tags, openToAll && type.speedKmh(TravelMode.FOOT) > 0, "foot");

        boolean roundabout = isRoundabout(tags);
        int direction = oneway(tags, type, roundabout);
        int bicycleDirection = bicycleOneway(tags, direction);
        int access = TravelMode.CAR.accessBits(car && direction >= 0, car && direction <= 0)
                | TravelMode.BICYCLE.accessBits(bicycle && bicycleDirection >= 0, bicycle && bicycleDirection <= 0)
                | TravelMode.FOOT.accessBits(foot, foot);
        if (access == 0) {
            return Optional.empty();
        }
        return Optional.of(new RoadWay(wayId, nameOf(tags), type, maxSpeed(tags.get("maxspeed")), roundabout, access));
    }

    /**
     * Roads that are open to cars but useless for getting anywhere: parking lot lanes and drive-through lanes.
     * The national graph leaves them out to stay smaller.
     */
    public static boolean isParkingLane(Map<String, String> tags) {
        String service = tags.getOrDefault("service", "");
        return "parking_aisle".equals(service) || "drive-through".equals(service);
    }

    /** Later keys are more specific ({@code vehicle} → {@code motor_vehicle} → {@code motorcar}) and win. */
    private static boolean permitted(Map<String, String> tags, boolean byDefault, String... keys) {
        boolean allowed = byDefault;
        for (String key : keys) {
            String value = tags.get(key);
            if (value == null) {
                continue;
            }
            if (DENIED.contains(value)) {
                allowed = false;
            } else if (GRANTED.contains(value)) {
                allowed = true;
            }
        }
        return allowed;
    }

    /** 1: only in the way's direction, -1: only against it, 0: both. */
    private static int oneway(Map<String, String> tags, RoadClass type, boolean roundabout) {
        String value = tags.get("oneway");
        if (value != null) {
            switch (value) {
                case "yes", "true", "1":
                    return 1;
                case "-1", "reverse":
                    return -1;
                case "no", "false", "0":
                    return 0;
                default:
                    break;
            }
        }
        return roundabout || type == RoadClass.MOTORWAY || type == RoadClass.MOTORWAY_LINK ? 1 : 0;
    }

    private static int bicycleOneway(Map<String, String> tags, int direction) {
        String value = tags.get("oneway:bicycle");
        if ("no".equals(value) || tags.getOrDefault("cycleway", "").startsWith("opposite")) {
            return 0;
        }
        return "yes".equals(value) ? 1 : direction;
    }

    private static boolean isRoundabout(Map<String, String> tags) {
        String junction = tags.get("junction");
        return "roundabout".equals(junction) || "circular".equals(junction);
    }

    static String nameOf(Map<String, String> tags) {
        for (String key : new String[] {"name", "name:tr", "ref"}) {
            String value = tags.get(key);
            if (value != null && !value.isBlank()) {
                String name = value.strip();
                return name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name;
            }
        }
        return null;
    }

    /** Signposted limit in km/h, 0 when missing or not understood. */
    static int maxSpeed(String value) {
        if (value == null) {
            return 0;
        }
        String limit = value.strip();
        Integer implicit = IMPLICIT_LIMITS.get(limit);
        if (implicit != null) {
            return implicit;
        }
        Matcher matcher = LEADING_NUMBER.matcher(limit);
        if (!matcher.find()) {
            return 0;
        }
        int number = Integer.parseInt(matcher.group(1));
        int kmh = matcher.group(2) == null ? number : (int) Math.round(number * 1.609);
        return kmh >= 5 && kmh <= 150 ? kmh : 0;
    }
}
