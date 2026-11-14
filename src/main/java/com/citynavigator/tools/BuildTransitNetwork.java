package com.citynavigator.tools;

import com.citynavigator.routing.CongestionProfile;
import com.citynavigator.routing.DayTime;
import com.citynavigator.routing.GeoMath;
import com.citynavigator.routing.TransitMode;
import com.citynavigator.routing.TransitNetwork;
import com.citynavigator.routing.TransitNetworkFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * Builds the public transport timetable of Istanbul that the application ships, from İBB's GTFS data sets: İETT's
 * buses and Metrobüs, and the ferries, metro, tram, Marmaray, minibuses and dolmuşes of the other operators.
 * <pre>
 *   java -cp target/classes com.citynavigator.tools.BuildTransitNetwork \
 *        src/main/resources/transit/istanbul-transit.bin.gz "İBB Açık Veri Portalı, ..." data/gtfs/ibb data/gtfs/iett
 * </pre>
 * The feeds need some repair, which this tool does: İETT's coordinates lost their decimal point to thousands
 * separators ({@code 410.191.700.005.564}), its line names were encoded twice ({@code KADIKÃ–Y}), its buses have times
 * only at the first and the last stop (the rest are worked out from the distance between stops), and the other feed
 * gives some lines as "every 5 minutes" (written out as trips here). Calendars are used as weekly patterns, without
 * their dates; services that ended long before the rest of their feed are left out. Where a feed draws the line a
 * vehicle follows (ferries, metro, Marmaray, minibuses, dolmuşes), the line is kept, simplified, so that a ride is drawn
 * along its real way rather than straight from stop to stop.
 *
 * <p>Buses, minibuses and dolmuşes run on the roads, yet their timetables give one duration for the whole day. With the
 * usual traffic of Istanbul (the profile of ADR-0009), each stretch between two stops is taken as the day's average and
 * made longer at the hours traffic along it is usually slower, shorter when it is faster. Traffic is read every few
 * hundred metres along the vehicle's way. A trip that also runs on weekdays gets weekday traffic. Metrobüs has its
 * own lane and keeps its timetable.
 */
public final class BuildTransitNetwork {

    /** Istanbul with a margin: a coordinate outside it is broken. */
    static final double MIN_LAT = 40.5;
    static final double MAX_LAT = 41.8;
    static final double MIN_LON = 27.5;
    static final double MAX_LON = 30.0;
    /** A service that ended this many days before the last one of its feed is history. */
    static final int STALE_DAYS = 365;
    /** A stop further than this from a line's shape means the shape does not belong to it. */
    static final double SHAPE_STOP_METERS = 200;
    /** Once a stop has been passed by this much, a later stretch of the shape is another visit. */
    static final double SHAPE_LOOKAHEAD_METERS = 1_000;
    /** Shapes are simplified to within this many metres of the original. */
    static final double SHAPE_TOLERANCE_METERS = 5;
    /** The hours a timetable's single duration is taken to be the average of. */
    static final int FIRST_SERVICE_HOUR = 6;
    static final int LAST_SERVICE_HOUR = 22;
    /** Traffic makes a stretch at most this much shorter or longer than the timetable says. */
    static final double MIN_TRAFFIC_SCALE = 0.7;
    static final double MAX_TRAFFIC_SCALE = 2.0;
    /** Traffic is read this often along a stretch (m). */
    static final double TRAFFIC_SAMPLE_METERS = 300;
    /** Times worked out with traffic are rounded to this many seconds: they are estimates, and round steps pack well. */
    static final int TRAFFIC_STEP_SECONDS = 15;
    static final String TRAFFIC_RESOURCE = "/traffic/istanbul-congestion.csv.gz";

    private static final Logger LOGGER = Logger.getLogger(BuildTransitNetwork.class.getName());
    private static final Locale TURKISH = Locale.forLanguageTag("tr");
    private static final Pattern METROBUS = Pattern.compile("34[A-ZÇĞİÖŞÜ]{0,2}");
    private static final String[] DAYS = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};

    private BuildTransitNetwork() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            throw new IllegalArgumentException("usage: BuildTransitNetwork <output.bin.gz> <source> <GTFS folder>...");
        }
        List<Path> feeds = Arrays.stream(args, 2, args.length).map(Path::of).toList();
        build(Path.of(args[0]), args[1], usualTraffic(), feeds);
    }

    /** Reads the feeds and writes the network; road vehicles' times follow the traffic profile. */
    static TransitNetwork build(Path output, String source, CongestionProfile traffic, List<Path> feeds)
            throws IOException {
        Collector collector = new Collector(source, traffic);
        for (Path feed : feeds) {
            collector.readFeed(feed);
        }
        TransitNetwork network = collector.build();
        try (OutputStream out = Files.newOutputStream(output)) {
            TransitNetworkFile.write(network, out);
        }
        LOGGER.info(() -> network.stopCount() + " stops, " + network.lineCount() + " lines, "
                + network.patternCount() + " patterns, " + collector.trips + " trips (" + collector.skipped
                + " left out) -> " + output);
        return network;
    }

    /** The usual traffic of Istanbul the application ships, or none when it is missing. */
    static CongestionProfile usualTraffic() throws IOException {
        try (InputStream resource = BuildTransitNetwork.class.getResourceAsStream(TRAFFIC_RESOURCE)) {
            if (resource == null) {
                LOGGER.warning(() -> TRAFFIC_RESOURCE + " is missing: road vehicles keep their timetables");
                return CongestionProfile.NONE;
            }
            return CongestionProfile.read(new GZIPInputStream(resource));
        }
    }

    /**
     * For each stretch between two stops and each {@link DayTime#slot()}, how much longer (above 1) or shorter (below
     * 1) than its timetable the stretch takes: the day's average share of free flow along it over the share usually
     * seen at that time, read at points every few hundred metres.
     *
     * @param stretches for each stretch its way, as {lat, lon} points
     */
    static double[][] trafficScales(List<List<double[]>> stretches, CongestionProfile traffic) {
        double[][] scales = new double[stretches.size()][DayTime.DAY_TYPES * 24];
        for (int stretch = 0; stretch < stretches.size(); stretch++) {
            List<double[]> samples = samples(stretches.get(stretch));
            double[][] usual = new double[samples.size()][DayTime.DAY_TYPES];
            for (int sample = 0; sample < samples.size(); sample++) {
                for (int dayType = 0; dayType < DayTime.DAY_TYPES; dayType++) {
                    usual[sample][dayType] = usualFactor(traffic, samples.get(sample)[0], samples.get(sample)[1],
                            dayType);
                }
            }
            for (int dayType = 0; dayType < DayTime.DAY_TYPES; dayType++) {
                for (int hour = 0; hour < 24; hour++) {
                    DayTime when = new DayTime(dayType, hour);
                    double sum = 0;
                    int count = 0;
                    for (int sample = 0; sample < samples.size(); sample++) {
                        double now = traffic.factor(samples.get(sample)[0], samples.get(sample)[1], when);
                        if (!Double.isNaN(now) && !Double.isNaN(usual[sample][dayType])) {
                            sum += usual[sample][dayType] / now;
                            count++;
                        }
                    }
                    scales[stretch][when.slot()] = count == 0 ? 1
                            : Math.clamp(sum / count, MIN_TRAFFIC_SCALE, MAX_TRAFFIC_SCALE);
                }
            }
        }
        return scales;
    }

    /**
     * A trip's times with the traffic of its day type: each stretch scaled for the hour the vehicle starts it, the times
     * after the first rounded to {@link #TRAFFIC_STEP_SECONDS}.
     */
    static int[] withTraffic(int[] times, double[][] scales, int dayType) {
        int[] adjusted = times.clone();
        double exact = times[0];
        for (int stretch = 0; stretch + 1 < times.length; stretch++) {
            int hour = Math.floorMod((int) exact / 3600, 24);
            exact += (times[stretch + 1] - times[stretch]) * scales[stretch][new DayTime(dayType, hour).slot()];
            int rounded = times[0] + (int) Math.round((exact - times[0]) / TRAFFIC_STEP_SECONDS) * TRAFFIC_STEP_SECONDS;
            adjusted[stretch + 1] = Math.max(adjusted[stretch], rounded);
        }
        return adjusted;
    }

    /** Points every {@link #TRAFFIC_SAMPLE_METERS} along the way (its middle when it is shorter than that). */
    static List<double[]> samples(List<double[]> way) {
        double length = 0;
        for (int point = 1; point < way.size(); point++) {
            length += GeoMath.distance(way.get(point - 1)[0], way.get(point - 1)[1], way.get(point)[0], way.get(point)[1]);
        }
        int count = Math.max(1, (int) Math.round(length / TRAFFIC_SAMPLE_METERS));
        List<double[]> samples = new ArrayList<>();
        double walked = 0;
        int point = 1;
        for (int sample = 0; sample < count; sample++) {
            double target = length * (sample + 0.5) / count;
            while (point < way.size() - 1 && walked + GeoMath.distance(way.get(point - 1)[0], way.get(point - 1)[1],
                    way.get(point)[0], way.get(point)[1]) < target) {
                walked += GeoMath.distance(way.get(point - 1)[0], way.get(point - 1)[1], way.get(point)[0],
                        way.get(point)[1]);
                point++;
            }
            double[] a = way.get(point - 1);
            double[] b = way.get(Math.min(point, way.size() - 1));
            double piece = GeoMath.distance(a[0], a[1], b[0], b[1]);
            double share = piece == 0 ? 0 : Math.clamp((target - walked) / piece, 0.0, 1.0);
            samples.add(new double[] {a[0] + (b[0] - a[0]) * share, a[1] + (b[1] - a[1]) * share});
        }
        return samples;
    }

    /** The day type whose traffic a trip gets: weekdays when it runs on any, otherwise Saturday or Sunday. */
    static int dayType(byte days) {
        if ((days & 0b0011111) != 0) {
            return DayTime.WEEKDAY;
        }
        return (days & 0b0100000) != 0 ? DayTime.SATURDAY : DayTime.SUNDAY;
    }

    private static double usualFactor(CongestionProfile traffic, double lat, double lon, int dayType) {
        double sum = 0;
        int count = 0;
        for (int hour = FIRST_SERVICE_HOUR; hour <= LAST_SERVICE_HOUR; hour++) {
            double factor = traffic.factor(lat, lon, new DayTime(dayType, hour));
            if (!Double.isNaN(factor)) {
                sum += factor;
                count++;
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    /** A coordinate, also when thousands separators replaced its decimal point; NaN when it is broken. */
    static double coordinate(String text, double min, double max) {
        try {
            double value = Double.parseDouble(text);
            if (value >= min && value <= max) {
                return value;
            }
        } catch (NumberFormatException e) {
            // perhaps 410.191.700.005.564: Istanbul's coordinates have two digits before the point
        }
        String digits = text.replace(".", "");
        if (digits.length() < 3 || !digits.chars().allMatch(Character::isDigit)) {
            return Double.NaN;
        }
        double value = Double.parseDouble(digits.substring(0, 2) + "." + digits.substring(2));
        return value >= min && value <= max ? value : Double.NaN;
    }

    /** {@code 06:40:00} in seconds; times after midnight go beyond 24 hours. -1 for an empty or broken time. */
    static int seconds(String text) {
        String[] parts = text.split(":");
        if (parts.length != 3) {
            return -1;
        }
        try {
            return Integer.parseInt(parts[0].strip()) * 3600 + Integer.parseInt(parts[1]) * 60
                    + Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Text that was UTF-8 read as Windows-1252 ({@code KADIKÃ–Y}) back as it was ({@code KADIKÖY}). */
    static String repaired(String text) {
        if (text.indexOf('Ã') < 0 && text.indexOf('Ä') < 0 && text.indexOf('Å') < 0) {
            return text;
        }
        Charset western = Charset.forName("windows-1252");
        byte[] bytes = new byte[text.length()];
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character < 256) {
                bytes[i] = (byte) character;
            } else {
                byte[] encoded = String.valueOf(character).getBytes(western);
                if (encoded.length != 1 || encoded[0] == '?') {
                    return text;
                }
                bytes[i] = encoded[0];
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return text;
        }
    }

    /** A name written in capitals ({@code RIFAT ILGAZ CADDESİ}) in Turkish title case ({@code Rıfat Ilgaz Caddesi}). */
    static String tidy(String text) {
        String clean = repaired(text).replaceAll("\\s+", " ").strip();
        long letters = clean.chars().filter(Character::isLetter).count();
        long capitals = clean.chars().filter(Character::isUpperCase).count();
        if (letters == 0 || capitals < letters * 0.8) {
            return clean;
        }
        String lower = clean.toLowerCase(TURKISH);
        StringBuilder title = new StringBuilder(lower.length());
        boolean wordStart = true;
        for (int i = 0; i < lower.length(); i++) {
            char character = lower.charAt(i);
            if (Character.isLetter(character)) {
                title.append(wordStart ? String.valueOf(character).toUpperCase(TURKISH) : String.valueOf(character));
                wordStart = false;
            } else {
                title.append(character);
                wordStart = !Character.isDigit(character);
            }
        }
        return title.toString();
    }

    static TransitMode mode(String routeType, String shortName, String agency) {
        return switch (routeType) {
            case "0" -> TransitMode.TRAM;
            case "1" -> agency.contains("TCDD") || shortName.startsWith("Marmaray")
                    ? TransitMode.RAIL : TransitMode.METRO;
            case "2" -> TransitMode.RAIL;
            case "4" -> TransitMode.FERRY;
            case "6" -> TransitMode.CABLE_CAR;
            case "7" -> TransitMode.FUNICULAR;
            case "9" -> TransitMode.MINIBUS;
            case "10" -> TransitMode.DOLMUS;
            default -> METROBUS.matcher(shortName).matches() ? TransitMode.METROBUS : TransitMode.BUS;
        };
    }

    /**
     * The name passengers know a line by ("M4", "14M", "Marmaray"; ferries, minibuses and dolmuşes have none) and what
     * it connects ("Kadıköy - Tavşantepe").
     */
    static String[] names(TransitMode mode, String shortName, String longName) {
        String code = repaired(shortName).strip();
        String description = tidy(longName.isBlank() ? shortName : longName);
        return switch (mode) {
            case FERRY -> new String[] {"", description};
            case MINIBUS, DOLMUS -> new String[] {"", tidy(code.isEmpty() ? longName : shortName)};
            case RAIL -> new String[] {"Marmaray", description};
            default -> new String[] {code, description};
        };
    }

    /**
     * Works out the times between the stops that have one, in proportion to the distance along the stops. False when
     * the first or the last time is missing or the times go back.
     */
    static boolean interpolate(double[] lat, double[] lon, int[] times) {
        int count = times.length;
        if (times[0] < 0 || times[count - 1] < 0) {
            return false;
        }
        double[] along = new double[count];
        for (int i = 1; i < count; i++) {
            along[i] = along[i - 1] + GeoMath.distance(lat[i - 1], lon[i - 1], lat[i], lon[i]);
        }
        int known = 0;
        for (int i = 1; i < count; i++) {
            if (times[i] < 0) {
                continue;
            }
            if (times[i] < times[known]) {
                return false;
            }
            double span = along[i] - along[known];
            for (int j = known + 1; j < i; j++) {
                double share = span > 0 ? (along[j] - along[known]) / span : (double) (j - known) / (i - known);
                times[j] = times[known] + (int) Math.round(share * (times[i] - times[known]));
            }
            known = i;
        }
        return true;
    }

    /**
     * For each stop the point of the shape nearest to it, going forward along the shape; {@code null} when a stop is
     * not near the shape.
     */
    static int[] fit(double[] shapeLat, double[] shapeLon, double[] stopLat, double[] stopLon) {
        int[] points = new int[stopLat.length];
        int from = 0;
        for (int stop = 0; stop < stopLat.length; stop++) {
            int best = -1;
            double bestMeters = Double.MAX_VALUE;
            for (int point = from; point < shapeLat.length; point++) {
                double meters = GeoMath.distance(stopLat[stop], stopLon[stop], shapeLat[point], shapeLon[point]);
                if (meters < bestMeters) {
                    best = point;
                    bestMeters = meters;
                } else if (bestMeters <= SHAPE_STOP_METERS && meters > bestMeters + SHAPE_LOOKAHEAD_METERS) {
                    break;
                }
            }
            if (best < 0 || bestMeters > SHAPE_STOP_METERS) {
                return null;
            }
            points[stop] = best;
            from = best;
        }
        return points;
    }

    /**
     * The shape from the first stop to the last, with only the points needed to stay within the tolerance (and the
     * points of the stops): Douglas–Peucker between each two stops.
     */
    static TransitNetwork.Shape simplified(double[] lat, double[] lon, int[] stopPoints) {
        int first = stopPoints[0];
        int last = stopPoints[stopPoints.length - 1];
        boolean[] keep = new boolean[lat.length];
        for (int point : stopPoints) {
            keep[point] = true;
        }
        for (int stop = 1; stop < stopPoints.length; stop++) {
            List<int[]> pending = new ArrayList<>();
            pending.add(new int[] {stopPoints[stop - 1], stopPoints[stop]});
            while (!pending.isEmpty()) {
                int[] span = pending.removeLast();
                int farthest = -1;
                double farthestMeters = SHAPE_TOLERANCE_METERS;
                for (int point = span[0] + 1; point < span[1]; point++) {
                    double meters = GeoMath.project(lat[point], lon[point], lat[span[0]], lon[span[0]], lat[span[1]],
                            lon[span[1]]).distanceMeters();
                    if (meters > farthestMeters) {
                        farthest = point;
                        farthestMeters = meters;
                    }
                }
                if (farthest > 0) {
                    keep[farthest] = true;
                    pending.add(new int[] {span[0], farthest});
                    pending.add(new int[] {farthest, span[1]});
                }
            }
        }
        int[] renumbered = new int[lat.length];
        List<double[]> kept = new ArrayList<>();
        for (int point = first; point <= last; point++) {
            if (keep[point]) {
                renumbered[point] = kept.size();
                kept.add(new double[] {lat[point], lon[point]});
            }
        }
        int[] stops = Arrays.stream(stopPoints).map(point -> renumbered[point]).toArray();
        if (kept.size() < 2) {
            return null;
        }
        return new TransitNetwork.Shape(kept.stream().mapToDouble(point -> point[0]).toArray(),
                kept.stream().mapToDouble(point -> point[1]).toArray(), stops);
    }

    /** Reads the feeds; {@link #build} makes the network. */
    static final class Collector {

        private final String source;
        private final CongestionProfile traffic;
        private final List<double[]> stopPoints = new ArrayList<>();
        private final List<String> stopNames = new ArrayList<>();
        private final Map<String, Integer> lines = new LinkedHashMap<>();
        private final List<Object[]> lineList = new ArrayList<>();
        private final Map<String, Bucket> buckets = new LinkedHashMap<>();
        /** The shapes of all feeds, by feed-prefixed id: {lat[], lon[]}. */
        private final Map<String, double[][]> shapes = new HashMap<>();
        private int feeds;
        int trips;
        int skipped;

        Collector(String source, CongestionProfile traffic) {
            this.source = source;
            this.traffic = traffic;
        }

        void readFeed(Path feed) throws IOException {
            String prefix = "f" + feeds++ + ":";
            Map<String, String> agencies = new HashMap<>();
            GtfsTable.read(feed, "agency", row -> agencies.put(row.get("agency_id"), row.get("agency_name")));
            FeedStops stops = new FeedStops(prefix);
            GtfsTable.read(feed, "stops", row -> stops.add(row.get("stop_id"),
                    coordinate(row.get("stop_lat"), MIN_LAT, MAX_LAT), coordinate(row.get("stop_lon"), MIN_LON, MAX_LON),
                    tidy(row.get("stop_name"))));
            Map<String, Integer> routeLines = new HashMap<>();
            GtfsTable.read(feed, "routes", row -> {
                String shortName = repaired(row.get("route_short_name"));
                TransitMode mode = mode(row.get("route_type"), shortName,
                        agencies.getOrDefault(row.get("agency_id"), ""));
                String[] names = names(mode, shortName, row.get("route_long_name"));
                routeLines.put(row.get("route_id"), line(names[0], names[1], mode));
            });
            Map<String, Byte> services = services(feed);
            readShapes(feed, prefix);
            Map<String, Object[]> tripInfo = new HashMap<>();
            GtfsTable.read(feed, "trips", row -> {
                Integer line = routeLines.get(row.get("route_id"));
                Byte days = services.get(row.get("service_id"));
                if (line != null && days != null) {
                    String shape = row.get("shape_id");
                    tripInfo.put(row.get("trip_id"), new Object[] {line, days, tidy(row.get("trip_headsign")),
                        shape.isEmpty() ? null : prefix + shape});
                }
            });
            Map<String, List<int[]>> frequencies = new HashMap<>();
            GtfsTable.read(feed, "frequencies", row -> {
                int start = seconds(row.get("start_time"));
                int end = seconds(row.get("end_time"));
                int headway = parse(row.get("headway_secs"));
                if (start >= 0 && end > start && headway > 0) {
                    frequencies.computeIfAbsent(row.get("trip_id"), key -> new ArrayList<>())
                            .add(new int[] {start, end, headway});
                }
            });
            Map<String, StopTimes> stopTimes = new LinkedHashMap<>();
            GtfsTable.read(feed, "stop_times", row -> {
                String trip = row.get("trip_id");
                if (tripInfo.containsKey(trip)) {
                    stopTimes.computeIfAbsent(trip, key -> new StopTimes()).add(parse(row.get("stop_sequence")),
                            stops.local(row.get("stop_id")), seconds(row.get("arrival_time")),
                            seconds(row.get("departure_time")));
                }
            });
            stopTimes.forEach((trip, times) -> {
                Object[] info = tripInfo.get(trip);
                addTrip((Integer) info[0], (Byte) info[1], (String) info[2], (String) info[3], times,
                        frequencies.get(trip), stops);
            });
        }

        private void readShapes(Path feed, String prefix) throws IOException {
            Map<String, List<double[]>> points = new HashMap<>();
            GtfsTable.read(feed, "shapes", row -> {
                double lat = coordinate(row.get("shape_pt_lat"), MIN_LAT, MAX_LAT);
                double lon = coordinate(row.get("shape_pt_lon"), MIN_LON, MAX_LON);
                if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                    points.computeIfAbsent(prefix + row.get("shape_id"), key -> new ArrayList<>())
                            .add(new double[] {parse(row.get("shape_pt_sequence")), lat, lon});
                }
            });
            points.forEach((id, list) -> {
                list.sort(Comparator.comparingDouble(point -> point[0]));
                shapes.put(id, new double[][] {list.stream().mapToDouble(point -> point[1]).toArray(),
                    list.stream().mapToDouble(point -> point[2]).toArray()});
            });
        }

        private int line(String name, String longName, TransitMode mode) {
            return lines.computeIfAbsent(mode + "|" + name + "|" + longName, key -> {
                lineList.add(new Object[] {name, longName, mode});
                return lineList.size() - 1;
            });
        }

        /** The weekdays each service runs on; services that ended long before the rest of the feed are left out. */
        private static Map<String, Byte> services(Path feed) throws IOException {
            Map<String, Byte> days = new HashMap<>();
            Map<String, LocalDate> ends = new HashMap<>();
            GtfsTable.read(feed, "calendar", row -> {
                byte mask = 0;
                for (int day = 0; day < DAYS.length; day++) {
                    if ("1".equals(row.get(DAYS[day]))) {
                        mask |= (byte) (1 << day);
                    }
                }
                if (mask != 0) {
                    days.put(row.get("service_id"), mask);
                    try {
                        ends.put(row.get("service_id"),
                                LocalDate.parse(row.get("end_date"), DateTimeFormatter.BASIC_ISO_DATE));
                    } catch (DateTimeParseException e) {
                        // no end date: the service stays
                    }
                }
            });
            ends.values().stream().max(Comparator.naturalOrder()).ifPresent(latest -> ends.forEach((service, end) -> {
                if (end.isBefore(latest.minusDays(STALE_DAYS))) {
                    days.remove(service);
                }
            }));
            return days;
        }

        private void addTrip(int line, byte days, String headsign, String shape, StopTimes rows, List<int[]> windows,
                             FeedStops stops) {
            int[] order = rows.inOrder();
            List<Integer> calls = new ArrayList<>();
            List<int[]> times = new ArrayList<>();
            for (int row : order) {
                int stop = rows.stop(row);
                if (stop < 0) {
                    continue;
                }
                if (!calls.isEmpty() && calls.getLast() == stop) {
                    // the same stop twice in a row: one call, arriving at the first and leaving at the second
                    times.getLast()[1] = rows.departure(row) >= 0 ? rows.departure(row) : times.getLast()[1];
                    continue;
                }
                calls.add(stop);
                times.add(new int[] {rows.arrival(row), rows.departure(row)});
            }
            int count = calls.size();
            if (count < 2) {
                skipped++;
                return;
            }
            int[] at = new int[count];
            double[] lat = new double[count];
            double[] lon = new double[count];
            for (int i = 0; i < count; i++) {
                int[] time = times.get(i);
                // leaving every stop but the last, arriving at the last
                at[i] = i < count - 1 ? (time[1] >= 0 ? time[1] : time[0]) : (time[0] >= 0 ? time[0] : time[1]);
                lat[i] = stops.lat(calls.get(i));
                lon[i] = stops.lon(calls.get(i));
            }
            if (!interpolate(lat, lon, at)) {
                skipped++;
                return;
            }
            int[] global = calls.stream().mapToInt(stop -> stops.global(stop, this)).toArray();
            Bucket bucket = buckets.computeIfAbsent(line + ":" + Arrays.toString(global),
                    key -> new Bucket(line, global));
            bucket.headsigns.merge(headsign.isEmpty() ? stopNames.get(global[count - 1]) : headsign, 1, Integer::sum);
            if (shape != null && shapes.containsKey(shape)) {
                bucket.shapes.merge(shape, 1, Integer::sum);
            }
            if (windows == null) {
                bucket.add(at, days);
                trips++;
                return;
            }
            // "every so many minutes" written out as trips
            for (int[] window : windows) {
                for (int start = window[0]; start < window[1]; start += window[2]) {
                    int[] shifted = new int[count];
                    for (int i = 0; i < count; i++) {
                        shifted[i] = at[i] - at[0] + start;
                    }
                    bucket.add(shifted, days);
                    trips++;
                }
            }
        }

        /** Road vehicles' trips with the usual traffic along their way. */
        private void applyTraffic(Bucket bucket, TransitNetwork.Shape shape) {
            TransitMode mode = (TransitMode) lineList.get(bucket.line)[2];
            boolean road = mode == TransitMode.BUS || mode == TransitMode.MINIBUS || mode == TransitMode.DOLMUS;
            if (!road || traffic.isEmpty()) {
                return;
            }
            List<List<double[]>> stretches = new ArrayList<>();
            for (int stop = 0; stop + 1 < bucket.stops.length; stop++) {
                List<double[]> way = new ArrayList<>();
                if (shape == null) {
                    way.add(stopPoints.get(bucket.stops[stop]));
                    way.add(stopPoints.get(bucket.stops[stop + 1]));
                } else {
                    for (int point = shape.stopPoints()[stop]; point <= shape.stopPoints()[stop + 1]; point++) {
                        way.add(new double[] {shape.lat()[point], shape.lon()[point]});
                    }
                }
                stretches.add(way);
            }
            double[][] scales = trafficScales(stretches, traffic);
            for (int trip = 0; trip < bucket.times.size(); trip++) {
                bucket.times.set(trip, withTraffic(bucket.times.get(trip), scales, dayType(bucket.days.get(trip))));
            }
        }

        int addStop(double lat, double lon, String name) {
            stopPoints.add(new double[] {lat, lon});
            stopNames.add(name);
            return stopNames.size() - 1;
        }

        TransitNetwork build() {
            TransitNetwork.Builder builder = TransitNetwork.builder(source);
            for (int stop = 0; stop < stopNames.size(); stop++) {
                builder.addStop(stopPoints.get(stop)[0], stopPoints.get(stop)[1], stopNames.get(stop));
            }
            for (Object[] line : lineList) {
                builder.addLine((String) line[0], (String) line[1], (TransitMode) line[2]);
            }
            for (Bucket bucket : buckets.values()) {
                String headsign = bucket.headsigns.entrySet().stream().max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey).orElse("");
                TransitNetwork.Shape shape = shapeOf(bucket);
                applyTraffic(bucket, shape);
                for (List<Integer> group : bucket.withoutOvertaking()) {
                    int length = bucket.stops.length;
                    int[] times = new int[group.size() * length];
                    byte[] days = new byte[group.size()];
                    for (int trip = 0; trip < group.size(); trip++) {
                        System.arraycopy(bucket.times.get(group.get(trip)), 0, times, trip * length, length);
                        days[trip] = bucket.days.get(group.get(trip));
                    }
                    builder.addPattern(bucket.line, headsign, bucket.stops, times, days, shape);
                }
            }
            return builder.build();
        }

        /** The shape most of the bucket's trips follow, fitted to its stops; {@code null} when there is none. */
        private TransitNetwork.Shape shapeOf(Bucket bucket) {
            return bucket.shapes.entrySet().stream().max(Map.Entry.comparingByValue()).map(entry -> {
                double[][] points = shapes.get(entry.getKey());
                double[] stopLat = Arrays.stream(bucket.stops).mapToDouble(stop -> stopPoints.get(stop)[0]).toArray();
                double[] stopLon = Arrays.stream(bucket.stops).mapToDouble(stop -> stopPoints.get(stop)[1]).toArray();
                int[] fitted = fit(points[0], points[1], stopLat, stopLon);
                return fitted == null ? null : simplified(points[0], points[1], fitted);
            }).orElse(null);
        }

        private static int parse(String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                return -1;
            }
        }
    }

    /** The stops of one feed; a stop joins the network when a trip first calls at it. */
    private static final class FeedStops {

        private final String prefix;
        private final Map<String, Integer> ids = new HashMap<>();
        private final List<double[]> points = new ArrayList<>();
        private final List<String> names = new ArrayList<>();
        private final List<Integer> global = new ArrayList<>();

        FeedStops(String prefix) {
            this.prefix = prefix;
        }

        void add(String id, double lat, double lon, String name) {
            if (!Double.isNaN(lat) && !Double.isNaN(lon) && !name.isEmpty() && !ids.containsKey(prefix + id)) {
                ids.put(prefix + id, names.size());
                points.add(new double[] {lat, lon});
                names.add(name);
                global.add(-1);
            }
        }

        /** The stop's number in this feed, or -1 for a stop the feed does not have (or whose place is broken). */
        int local(String id) {
            return ids.getOrDefault(prefix + id, -1);
        }

        double lat(int stop) {
            return points.get(stop)[0];
        }

        double lon(int stop) {
            return points.get(stop)[1];
        }

        int global(int stop, Collector collector) {
            if (global.get(stop) < 0) {
                global.set(stop, collector.addStop(lat(stop), lon(stop), names.get(stop)));
            }
            return global.get(stop);
        }
    }

    /** The rows of one trip in stop_times, in the order the file gives them. */
    private static final class StopTimes {

        private int[] values = new int[16];
        private int size;

        void add(int sequence, int stop, int arrival, int departure) {
            if (size + 4 > values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = sequence;
            values[size++] = stop;
            values[size++] = arrival;
            values[size++] = departure;
        }

        /** The rows in the order of their stop sequence. */
        int[] inOrder() {
            return java.util.stream.IntStream.range(0, size / 4).boxed()
                    .sorted(Comparator.comparingInt(row -> values[row * 4])).mapToInt(Integer::intValue).toArray();
        }

        int stop(int row) {
            return values[row * 4 + 1];
        }

        int arrival(int row) {
            return values[row * 4 + 2];
        }

        int departure(int row) {
            return values[row * 4 + 3];
        }
    }

    /** The trips of one line that call at the same stops. */
    private static final class Bucket {

        private final int line;
        private final int[] stops;
        private final List<int[]> times = new ArrayList<>();
        private final List<Byte> days = new ArrayList<>();
        private final Map<String, Integer> headsigns = new HashMap<>();
        private final Map<String, Integer> shapes = new HashMap<>();

        Bucket(int line, int[] stops) {
            this.line = line;
            this.stops = stops;
        }

        void add(int[] tripTimes, byte tripDays) {
            times.add(tripTimes);
            days.add(tripDays);
        }

        /**
         * The trips in the order they leave, split into groups in which no trip overtakes another (as RAPTOR needs);
         * trips with the same times are one trip running on all their days.
         */
        List<List<Integer>> withoutOvertaking() {
            List<Integer> order = new ArrayList<>(java.util.stream.IntStream.range(0, times.size()).boxed().toList());
            order.sort((a, b) -> Arrays.compare(times.get(a), times.get(b)));
            List<Integer> distinct = new ArrayList<>();
            for (int trip : order) {
                if (!distinct.isEmpty() && Arrays.equals(times.get(distinct.getLast()), times.get(trip))) {
                    int kept = distinct.getLast();
                    days.set(kept, (byte) (days.get(kept) | days.get(trip)));
                } else {
                    distinct.add(trip);
                }
            }
            List<List<Integer>> groups = new ArrayList<>();
            for (int trip : distinct) {
                List<Integer> fits = null;
                for (List<Integer> group : groups) {
                    if (notBefore(times.get(trip), times.get(group.getLast()))) {
                        fits = group;
                        break;
                    }
                }
                if (fits == null) {
                    fits = new ArrayList<>();
                    groups.add(fits);
                }
                fits.add(trip);
            }
            return groups;
        }

        private static boolean notBefore(int[] later, int[] earlier) {
            for (int i = 0; i < later.length; i++) {
                if (later[i] < earlier[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}
