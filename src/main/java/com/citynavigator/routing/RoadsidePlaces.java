package com.citynavigator.routing;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The places along a route. They are kept in a grid of cells about two kilometres across, so that a search looks only
 * at the cells the route passes, not at the tens of thousands of places in the country.
 */
public final class RoadsidePlaces {

    /** The grid's cell size in degrees: about 2.2 km north to south and 1.7 km east to west in Turkey. */
    static final double CELL_DEGREES = 0.02;
    /** Two places of a kind this close are one: a station drawn both as a building and as a point. */
    static final double SAME_PLACE_METERS = 25;
    /**
     * Behind the start or beyond the destination only places this close count: what lies behind the traveller is
     * not on the way.
     */
    static final double END_METERS = 50;

    private final Map<RoadsidePlace.Kind, Map<Long, List<RoadsidePlace>>> cells =
            new EnumMap<>(RoadsidePlace.Kind.class);
    private final int size;

    public RoadsidePlaces(Collection<RoadsidePlace> places) {
        for (RoadsidePlace place : places) {
            cells.computeIfAbsent(place.kind(), kind -> new HashMap<>())
                    .computeIfAbsent(cell(row(place.lat()), column(place.lon())), key -> new ArrayList<>())
                    .add(place);
        }
        size = places.size();
    }

    /**
     * Reads the list the {@code BuildPoiIndex} tool writes: one place per line as {@code KIND;lat;lon;name}, comment
     * lines starting with {@code #}.
     */
    public static RoadsidePlaces read(BufferedReader reader) throws IOException {
        List<RoadsidePlace> places = new ArrayList<>();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] fields = line.split(";", 4);
            if (fields.length < 4) {
                throw new IOException("not a place: " + line);
            }
            try {
                places.add(new RoadsidePlace(RoadsidePlace.Kind.valueOf(fields[0]), Double.parseDouble(fields[1]),
                        Double.parseDouble(fields[2]), fields[3]));
            } catch (IllegalArgumentException e) {
                throw new IOException("not a place: " + line, e);
            }
        }
        return new RoadsidePlaces(places);
    }

    public int size() {
        return size;
    }

    /**
     * The places of a kind within {@code maxOffsetMeters} of the line, each where it comes closest to the line, in
     * the order the line passes them. Places behind the start or beyond the destination are left out unless they are
     * all but there.
     *
     * @param line the route from its start to its destination
     */
    public List<Found> along(List<GeoPoint> line, RoadsidePlace.Kind kind, double maxOffsetMeters) {
        Map<Long, List<RoadsidePlace>> grid = cells.getOrDefault(kind, Map.of());
        Map<RoadsidePlace, Found> closest = new HashMap<>();
        double latMargin = maxOffsetMeters / GeoMath.METERS_PER_DEGREE;
        double start = 0;
        for (int i = 0; i + 1 < line.size(); i++) {
            GeoPoint a = line.get(i);
            GeoPoint b = line.get(i + 1);
            double length = GeoMath.distance(a, b);
            double lonMargin = latMargin / Math.cos(Math.toRadians(Math.max(Math.abs(a.lat()), Math.abs(b.lat()))));
            int lastRow = row(Math.max(a.lat(), b.lat()) + latMargin);
            int lastColumn = column(Math.max(a.lon(), b.lon()) + lonMargin);
            for (int row = row(Math.min(a.lat(), b.lat()) - latMargin); row <= lastRow; row++) {
                for (int column = column(Math.min(a.lon(), b.lon()) - lonMargin); column <= lastColumn; column++) {
                    for (RoadsidePlace place : grid.getOrDefault(cell(row, column), List.of())) {
                        Found found = locate(place, a, b, start, length);
                        if (found.offsetMeters() <= maxOffsetMeters) {
                            closest.merge(place, found, (known, now) ->
                                    now.offsetMeters() < known.offsetMeters() ? now : known);
                        }
                    }
                }
            }
            start += length;
        }
        double total = start;
        return closest.values().stream()
                .filter(found -> found.offsetMeters() <= END_METERS
                        || found.alongMeters() > 0 && found.alongMeters() < total)
                .sorted(Comparator.comparingDouble(Found::alongMeters))
                .toList();
    }

    /**
     * At most {@code limit} of the places found, spread over the route rather than bunched at its start: the route is
     * cut into {@code limit} equal pieces and each piece keeps the place nearest the road; the places left over fill
     * the room that empty pieces leave, nearest the road first. Places of a kind a few metres apart count once.
     */
    public static List<Found> spread(List<Found> found, double routeMeters, int limit) {
        List<Found> nearestFirst = found.stream()
                .sorted(Comparator.comparingDouble(Found::offsetMeters).thenComparingDouble(Found::alongMeters))
                .toList();
        double piece = Math.max(1, routeMeters / limit);
        Set<Integer> filled = new HashSet<>();
        List<Found> kept = new ArrayList<>();
        List<Found> leftOver = new ArrayList<>();
        for (Found candidate : nearestFirst) {
            if (sameAsKept(candidate, kept)) {
                continue;
            }
            if (filled.add(Math.min(limit - 1, (int) (candidate.alongMeters() / piece)))) {
                kept.add(candidate);
            } else {
                leftOver.add(candidate);
            }
        }
        for (Found candidate : leftOver) {
            if (kept.size() >= limit) {
                break;
            }
            if (!sameAsKept(candidate, kept)) {
                kept.add(candidate);
            }
        }
        kept.sort(Comparator.comparingDouble(Found::alongMeters));
        return kept;
    }

    private static boolean sameAsKept(Found candidate, List<Found> kept) {
        RoadsidePlace place = candidate.place();
        return kept.stream().anyMatch(other -> other.place().kind() == place.kind() && GeoMath.distance(
                other.place().lat(), other.place().lon(), place.lat(), place.lon()) < SAME_PLACE_METERS);
    }

    /** Where the place comes closest to the segment a-b, which starts {@code start} metres into the route. */
    private static Found locate(RoadsidePlace place, GeoPoint a, GeoPoint b, double start, double length) {
        GeoMath.Projection projection = GeoMath.project(place.lat(), place.lon(), a.lat(), a.lon(), b.lat(), b.lon());
        // the cross product of the direction of travel and the way to the place: negative means the place is on the
        // right
        double scale = Math.cos(Math.toRadians(a.lat()));
        double cross = (b.lon() - a.lon()) * scale * (place.lat() - a.lat())
                - (b.lat() - a.lat()) * (place.lon() - a.lon()) * scale;
        return new Found(place, start + projection.fraction() * length, projection.distanceMeters(), cross < 0);
    }

    private static int row(double lat) {
        return (int) Math.floor(lat / CELL_DEGREES);
    }

    private static int column(double lon) {
        return (int) Math.floor(lon / CELL_DEGREES);
    }

    private static long cell(int row, int column) {
        return (long) row << 32 | column & 0xffff_ffffL;
    }

    /**
     * A place near the route.
     *
     * @param alongMeters  how far into the route it is reached
     * @param offsetMeters how far it lies from the route there
     * @param onTheRight   whether it lies on the right of the direction of travel
     */
    public record Found(RoadsidePlace place, double alongMeters, double offsetMeters, boolean onTheRight) {
    }
}
