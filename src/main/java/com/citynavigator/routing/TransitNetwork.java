package com.citynavigator.routing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A city's public transport as a timetable. A line (a ferry route, the M4, bus 14M) runs as patterns: trips that call
 * at the same stops in the same order. For every trip of a pattern the network knows its time at each stop and the
 * days it runs. Walking transfers between stops close to each other are worked out when the network is built.
 *
 * <p>Times are seconds after the midnight that starts the trip's service day; a trip that runs past midnight has
 * times beyond 24 hours, as in GTFS.
 */
public final class TransitNetwork {

    /** Stops this close (straight line) are linked by a walking transfer. */
    public static final double TRANSFER_METERS = 400;
    /** Walking speed, 4.8 km/h. */
    public static final double WALK_METERS_PER_SECOND = 4.8 / 3.6;
    /** Streets are not straight: a walk is about this much longer than the straight line. */
    public static final double WALK_DETOUR = 1.3;
    /** The grid that finds the stops near a point: cells of about 550 m north to south. */
    static final double CELL_DEGREES = 0.005;

    private final String source;
    private final double[] stopLat;
    private final double[] stopLon;
    private final String[] stopName;
    private final String[] lineName;
    private final String[] lineLongName;
    private final TransitMode[] lineMode;
    final int[] patternLine;
    final String[] patternHeadsign;
    final int[][] patternStops;
    /** Per pattern, trip after trip, the time at each stop: {@code times[trip * stops + index]}. */
    final int[][] patternTimes;
    /** Per pattern and trip, the days it runs: bit 0 is Monday, bit 6 Sunday. */
    final byte[][] patternDays;
    /** Per pattern the line the vehicles follow, or {@code null} when only the stops are known. */
    private final Shape[] patternShape;
    /** Which patterns call at each stop and where in the pattern, as compressed rows. */
    final int[] callStart;
    final int[] callPattern;
    final int[] callIndex;
    /** The walking transfers from each stop, as compressed rows. */
    final int[] walkStart;
    final int[] walkTo;
    final int[] walkSeconds;
    private final Map<Long, int[]> cells = new HashMap<>();
    private final double[] bounds = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};

    private TransitNetwork(Builder builder) {
        source = builder.source;
        int stops = builder.stopNames.size();
        stopLat = new double[stops];
        stopLon = new double[stops];
        stopName = builder.stopNames.toArray(String[]::new);
        for (int stop = 0; stop < stops; stop++) {
            stopLat[stop] = builder.stopPoints.get(stop)[0];
            stopLon[stop] = builder.stopPoints.get(stop)[1];
            bounds[0] = Math.min(bounds[0], stopLat[stop]);
            bounds[1] = Math.min(bounds[1], stopLon[stop]);
            bounds[2] = Math.max(bounds[2], stopLat[stop]);
            bounds[3] = Math.max(bounds[3], stopLon[stop]);
        }
        lineName = builder.lineNames.toArray(String[]::new);
        lineLongName = builder.lineLongNames.toArray(String[]::new);
        lineMode = builder.lineModes.toArray(TransitMode[]::new);
        int patterns = builder.patterns.size();
        patternLine = new int[patterns];
        patternHeadsign = new String[patterns];
        patternStops = new int[patterns][];
        patternTimes = new int[patterns][];
        patternDays = new byte[patterns][];
        patternShape = new Shape[patterns];
        for (int pattern = 0; pattern < patterns; pattern++) {
            Builder.Pattern built = builder.patterns.get(pattern);
            patternLine[pattern] = built.line();
            patternHeadsign[pattern] = built.headsign();
            patternStops[pattern] = built.stops();
            patternTimes[pattern] = built.times();
            patternDays[pattern] = built.days();
            patternShape[pattern] = built.shape();
        }
        // which patterns call where
        callStart = new int[stops + 1];
        for (int[] calls : patternStops) {
            for (int stop : calls) {
                callStart[stop + 1]++;
            }
        }
        for (int stop = 0; stop < stops; stop++) {
            callStart[stop + 1] += callStart[stop];
        }
        callPattern = new int[callStart[stops]];
        callIndex = new int[callStart[stops]];
        int[] next = Arrays.copyOf(callStart, stops);
        for (int pattern = 0; pattern < patterns; pattern++) {
            for (int index = 0; index < patternStops[pattern].length; index++) {
                int slot = next[patternStops[pattern][index]]++;
                callPattern[slot] = pattern;
                callIndex[slot] = index;
            }
        }
        // the grid, then the walking transfers it finds
        Map<Long, List<Integer>> grid = new HashMap<>();
        for (int stop = 0; stop < stops; stop++) {
            grid.computeIfAbsent(cell(row(stopLat[stop]), column(stopLon[stop])), key -> new ArrayList<>()).add(stop);
        }
        grid.forEach((key, members) -> cells.put(key, members.stream().mapToInt(Integer::intValue).toArray()));
        walkStart = new int[stops + 1];
        List<int[]> walks = new ArrayList<>();
        for (int stop = 0; stop < stops; stop++) {
            for (int near : stopsWithin(stopLat[stop], stopLon[stop], TRANSFER_METERS)) {
                if (near != stop) {
                    walks.add(new int[] {near, walkSeconds(GeoMath.distance(stopLat[stop], stopLon[stop],
                            stopLat[near], stopLon[near]))});
                }
            }
            walkStart[stop + 1] = walks.size();
        }
        walkTo = walks.stream().mapToInt(walk -> walk[0]).toArray();
        walkSeconds = walks.stream().mapToInt(walk -> walk[1]).toArray();
    }

    public static Builder builder(String source) {
        return new Builder(source);
    }

    /** How long walking takes for a straight-line distance, in seconds. */
    public static int walkSeconds(double straightMeters) {
        return (int) Math.round(straightMeters * WALK_DETOUR / WALK_METERS_PER_SECOND);
    }

    /** Where the timetables come from and how old they are, for the attribution. */
    public String source() {
        return source;
    }

    public int stopCount() {
        return stopName.length;
    }

    public GeoPoint stop(int stop) {
        return new GeoPoint(stopLat[stop], stopLon[stop]);
    }

    public String stopName(int stop) {
        return stopName[stop];
    }

    public int lineCount() {
        return lineName.length;
    }

    public String lineName(int line) {
        return lineName[line];
    }

    public String lineLongName(int line) {
        return lineLongName[line];
    }

    public TransitMode lineMode(int line) {
        return lineMode[line];
    }

    public int patternCount() {
        return patternStops.length;
    }

    public int patternLine(int pattern) {
        return patternLine[pattern];
    }

    public String patternHeadsign(int pattern) {
        return patternHeadsign[pattern];
    }

    public int patternLength(int pattern) {
        return patternStops[pattern].length;
    }

    public int patternStop(int pattern, int index) {
        return patternStops[pattern][index];
    }

    public int tripCount(int pattern) {
        return patternDays[pattern].length;
    }

    /** The trip's time at the stop at {@code index} in its pattern. */
    public int time(int pattern, int trip, int index) {
        return patternTimes[pattern][trip * patternStops[pattern].length + index];
    }

    /** Whether the trip runs on the day, 0 Monday to 6 Sunday. */
    public boolean runs(int pattern, int trip, int day) {
        return (patternDays[pattern][trip] >> day & 1) != 0;
    }

    public byte days(int pattern, int trip) {
        return patternDays[pattern][trip];
    }

    /** The line the pattern's vehicles follow, or {@code null} when the timetable gives only the stops. */
    public Shape shape(int pattern) {
        return patternShape[pattern];
    }

    /**
     * The way a vehicle of the pattern goes from the stop at {@code from} to the stop at {@code to}: along its line
     * when the timetable has one, otherwise straight from stop to stop.
     */
    public List<GeoPoint> rideLine(int pattern, int from, int to) {
        List<GeoPoint> line = new ArrayList<>();
        Shape shape = patternShape[pattern];
        if (shape == null) {
            for (int index = from; index <= to; index++) {
                line.add(stop(patternStops[pattern][index]));
            }
            return line;
        }
        for (int point = shape.stopPoints()[from]; point <= shape.stopPoints()[to]; point++) {
            line.add(new GeoPoint(shape.lat()[point], shape.lon()[point]));
        }
        return line;
    }

    /** Whether the point is within the margin of the area the stops cover. */
    public boolean covers(GeoPoint point, double marginMeters) {
        if (stopLat.length == 0) {
            return false;
        }
        double latMargin = marginMeters / GeoMath.METERS_PER_DEGREE;
        double lonMargin = latMargin / Math.cos(Math.toRadians(point.lat()));
        return point.lat() >= bounds[0] - latMargin && point.lat() <= bounds[2] + latMargin
                && point.lon() >= bounds[1] - lonMargin && point.lon() <= bounds[3] + lonMargin;
    }

    /** The stops within the distance (straight line) of the point. */
    public int[] stopsWithin(GeoPoint point, double meters) {
        return stopsWithin(point.lat(), point.lon(), meters);
    }

    private int[] stopsWithin(double lat, double lon, double meters) {
        double latMargin = meters / GeoMath.METERS_PER_DEGREE;
        double lonMargin = latMargin / Math.cos(Math.toRadians(lat));
        List<Integer> found = new ArrayList<>();
        for (int row = row(lat - latMargin); row <= row(lat + latMargin); row++) {
            for (int column = column(lon - lonMargin); column <= column(lon + lonMargin); column++) {
                for (int stop : cells.getOrDefault(cell(row, column), new int[0])) {
                    if (GeoMath.distance(lat, lon, stopLat[stop], stopLon[stop]) <= meters) {
                        found.add(stop);
                    }
                }
            }
        }
        return found.stream().mapToInt(Integer::intValue).toArray();
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
     * Collects stops, lines and patterns; {@link #build} works out the rest.
     */
    public static final class Builder {

        private final String source;
        private final List<double[]> stopPoints = new ArrayList<>();
        private final List<String> stopNames = new ArrayList<>();
        private final List<String> lineNames = new ArrayList<>();
        private final List<String> lineLongNames = new ArrayList<>();
        private final List<TransitMode> lineModes = new ArrayList<>();
        private final List<Pattern> patterns = new ArrayList<>();

        private Builder(String source) {
            this.source = source;
        }

        public int addStop(double lat, double lon, String name) {
            new GeoPoint(lat, lon);
            stopPoints.add(new double[] {lat, lon});
            stopNames.add(name);
            return stopNames.size() - 1;
        }

        public int addLine(String name, String longName, TransitMode mode) {
            lineNames.add(name);
            lineLongNames.add(longName);
            lineModes.add(mode);
            return lineNames.size() - 1;
        }

        /** A pattern whose line is known only by its stops. */
        public Builder addPattern(int line, String headsign, int[] stops, int[] times, byte[] days) {
            return addPattern(line, headsign, stops, times, days, null);
        }

        /**
         * A pattern of a line and its trips.
         *
         * @param stops the stops in the order the trips call at them, at least two
         * @param times trip after trip, the time at each stop; trips in the order they leave, none overtaking another
         * @param days  for each trip the days it runs, bit 0 Monday to bit 6 Sunday
         * @param shape the line the vehicles follow, or {@code null}
         */
        public Builder addPattern(int line, String headsign, int[] stops, int[] times, byte[] days, Shape shape) {
            if (line < 0 || line >= lineNames.size()) {
                throw new IllegalArgumentException("no line " + line);
            }
            if (stops.length < 2 || days.length == 0 || times.length != stops.length * days.length) {
                throw new IllegalArgumentException("a pattern needs two stops and a time per trip and stop");
            }
            for (int stop : stops) {
                if (stop < 0 || stop >= stopNames.size()) {
                    throw new IllegalArgumentException("no stop " + stop);
                }
            }
            for (int trip = 0; trip < days.length; trip++) {
                for (int index = 0; index < stops.length; index++) {
                    int at = trip * stops.length + index;
                    if (index > 0 && times[at] < times[at - 1]) {
                        throw new IllegalArgumentException("a trip goes back in time");
                    }
                    if (trip > 0 && times[at] < times[at - stops.length]) {
                        throw new IllegalArgumentException("a trip overtakes the one before it");
                    }
                }
            }
            if (shape != null && shape.stopPoints().length != stops.length) {
                throw new IllegalArgumentException("the shape needs a point for every stop");
            }
            patterns.add(new Pattern(line, headsign, stops.clone(), times.clone(), days.clone(), shape));
            return this;
        }

        public TransitNetwork build() {
            return new TransitNetwork(this);
        }

        private record Pattern(int line, String headsign, int[] stops, int[] times, byte[] days, Shape shape) {
        }
    }

    /**
     * The line a pattern's vehicles follow: its points, and for each stop of the pattern the point it is at.
     */
    public record Shape(double[] lat, double[] lon, int[] stopPoints) {

        public Shape {
            if (lat.length != lon.length || lat.length < 2) {
                throw new IllegalArgumentException("a shape needs at least two points");
            }
            for (int stop = 0; stop < stopPoints.length; stop++) {
                if (stopPoints[stop] < 0 || stopPoints[stop] >= lat.length
                        || stop > 0 && stopPoints[stop] < stopPoints[stop - 1]) {
                    throw new IllegalArgumentException("the stops must lie along the shape, in order");
                }
            }
        }
    }
}
