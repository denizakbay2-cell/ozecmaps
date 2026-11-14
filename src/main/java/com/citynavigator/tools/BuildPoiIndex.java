package com.citynavigator.tools;

import com.citynavigator.osm.OplReader;
import com.citynavigator.routing.RoadsidePlace;
import com.citynavigator.routing.RoadsidePlace.Kind;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * Keeps the places that matter on a trip (fuel and charging stations, pharmacies, banks and ATMs, markets) from an
 * OpenStreetMap extract, in the compact list the application bundles for searching along a route: one line per
 * place, {@code KIND;lat;lon;name} (see {@link com.citynavigator.routing.RoadsidePlaces#read}). A place drawn as a
 * building gets the middle of its outline.
 * <pre>
 *   java -cp target/classes com.citynavigator.tools.BuildPoiIndex data/turkey-pois.opl \
 *        src/main/resources/pois/turkey-pois.csv.gz 2026-11-13T21:21:02Z
 * </pre>
 */
public final class BuildPoiIndex {

    private static final Logger LOGGER = Logger.getLogger(BuildPoiIndex.class.getName());
    private static final int MAX_NAME = 80;

    private BuildPoiIndex() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("usage: BuildPoiIndex <extract.opl> <output.csv.gz> [data timestamp]");
        }
        Collector collector = new Collector();
        try (BufferedReader reader = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)) {
            OplReader.read(reader, collector);
        }
        List<RoadsidePlace> rows = collector.rows();
        Path output = Path.of(args[1]);
        try (OutputStream file = Files.newOutputStream(output);
             PrintWriter writer = new PrintWriter(new OutputStreamWriter(new GZIPOutputStream(file),
                     StandardCharsets.UTF_8))) {
            write(rows, args.length > 2 ? args[2] : "unknown", writer);
        }
        LOGGER.info(() -> rows.size() + " places written to " + output);
    }

    static void write(List<RoadsidePlace> rows, String dataTimestamp, PrintWriter writer) {
        writer.println("# places along routes: KIND;lat;lon;name");
        writer.println("# OpenStreetMap data of " + dataTimestamp + ", (c) OpenStreetMap contributors, ODbL");
        rows.stream().sorted(Comparator.comparing(RoadsidePlace::kind).thenComparingDouble(RoadsidePlace::lat)
                        .thenComparingDouble(RoadsidePlace::lon))
                .forEach(row -> writer.println(String.format(Locale.ROOT, "%s;%.5f;%.5f;%s", row.kind(), row.lat(),
                        row.lon(), row.name())));
    }

    /**
     * What a traveller would look for at a place with these tags, or empty for a place the list does not keep.
     */
    static Optional<Kind> kind(Map<String, String> tags) {
        String amenity = tags.getOrDefault("amenity", "");
        String shop = tags.getOrDefault("shop", "");
        return Optional.ofNullable(switch (amenity) {
            case "fuel" -> Kind.FUEL;
            case "charging_station" -> Kind.CHARGING;
            case "pharmacy" -> Kind.PHARMACY;
            case "atm", "bank" -> Kind.MONEY;
            default -> "supermarket".equals(shop) || "convenience".equals(shop) ? Kind.MARKET : null;
        });
    }

    /** The Turkish name, the name, the brand, the operator, or what the place is. */
    static String name(Map<String, String> tags, Kind kind) {
        for (String key : List.of("name:tr", "name", "brand", "operator")) {
            String value = tags.get(key);
            if (value != null && !value.isBlank()) {
                String clean = value.replace(';', ',').replaceAll("\\s+", " ").strip();
                return clean.length() > MAX_NAME ? clean.substring(0, MAX_NAME) : clean;
            }
        }
        return switch (kind) {
            case FUEL -> "Akaryakıt istasyonu";
            case CHARGING -> "Şarj istasyonu";
            case PHARMACY -> "Eczane";
            case MONEY -> "atm".equals(tags.get("amenity")) ? "ATM" : "Banka";
            case MARKET -> "Market";
        };
    }

    /** Remembers where nodes are, so that a place drawn as a way can be placed at the middle of its outline. */
    static final class Collector implements OplReader.Handler {

        private final Map<Long, double[]> nodes = new HashMap<>();
        private final List<RoadsidePlace> rows = new ArrayList<>();

        @Override
        public void node(long id, double lat, double lon) {
            nodes.put(id, new double[] {lat, lon});
        }

        @Override
        public void taggedNode(long id, double lat, double lon, Map<String, String> tags) {
            kind(tags).ifPresent(kind -> rows.add(new RoadsidePlace(kind, lat, lon, name(tags, kind))));
        }

        @Override
        public void way(long id, Map<String, String> tags, long[] refs) {
            Optional<Kind> kind = kind(tags);
            if (kind.isEmpty()) {
                return;
            }
            double lat = 0;
            double lon = 0;
            int count = 0;
            // a closed outline repeats its first node at the end; count it once
            int distinct = refs.length > 1 && refs[0] == refs[refs.length - 1] ? refs.length - 1 : refs.length;
            for (int i = 0; i < distinct; i++) {
                double[] point = nodes.get(refs[i]);
                if (point != null) {
                    lat += point[0];
                    lon += point[1];
                    count++;
                }
            }
            if (count > 0) {
                rows.add(new RoadsidePlace(kind.get(), lat / count, lon / count, name(tags, kind.get())));
            }
        }

        List<RoadsidePlace> rows() {
            return rows;
        }
    }
}
