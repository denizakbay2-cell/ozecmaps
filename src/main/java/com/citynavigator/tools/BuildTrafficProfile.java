package com.citynavigator.tools;

import com.citynavigator.routing.DayTime;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * Condenses the hourly traffic density data of Istanbul Metropolitan Municipality (open data, one CSV per month:
 * average speed and number of vehicles per geohash cell and hour) into a congestion profile: for every cell, the
 * usual share of its free-flow speed on weekdays, Saturdays and Sundays, hour by hour.
 * <pre>
 *   java -cp target/classes com.citynavigator.tools.BuildTrafficProfile \
 *        src/main/resources/traffic/istanbul-congestion.csv.gz data/traffic_density_2024*.csv
 * </pre>
 */
public final class BuildTrafficProfile {

    /** A slot needs this many vehicle observations to say anything. */
    static final int MIN_VEHICLES = 20;
    /** A cell needs data for this many of its 72 slots to be used at all. */
    static final int MIN_SLOTS = 24;
    /** Free flow is the mean of a cell's fastest slots, not a single lucky hour. */
    static final int FREE_FLOW_SLOTS = 3;
    static final int SLOWEST_PERCENT = 10;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Logger LOGGER = Logger.getLogger(BuildTrafficProfile.class.getName());

    private BuildTrafficProfile() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            throw new IllegalArgumentException("usage: BuildTrafficProfile <output.csv.gz> <density.csv>...");
        }
        Map<String, Cell> cells = new TreeMap<>();
        for (int i = 1; i < args.length; i++) {
            try (BufferedReader reader = Files.newBufferedReader(Path.of(args[i]), StandardCharsets.UTF_8)) {
                read(reader, cells);
            }
        }
        int written;
        try (OutputStream file = Files.newOutputStream(Path.of(args[0]));
             PrintWriter out = new PrintWriter(new OutputStreamWriter(new GZIPOutputStream(file), StandardCharsets.UTF_8))) {
            written = write(cells, out);
        }
        int kept = written;
        LOGGER.info(() -> kept + " of " + cells.size() + " cells written to " + args[0]);
    }

    /** Adds the rows of one month: "DATE_TIME","LATITUDE","LONGITUDE",GEOHASH,MIN,MAX,AVERAGE_SPEED,VEHICLES. */
    static void read(BufferedReader reader, Map<String, Cell> cells) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            String[] parts = line.replace("\"", "").split(",");
            if (parts.length < 8 || !Character.isDigit(parts[0].charAt(0))) {
                continue;
            }
            DayTime when = DayTime.of(LocalDateTime.parse(parts[0].strip(), TIME));
            int vehicles = Integer.parseInt(parts[7].strip());
            double averageSpeed = Double.parseDouble(parts[6].strip());
            cells.computeIfAbsent(parts[3].strip(), key -> new Cell()).add(when.slot(), averageSpeed, vehicles);
        }
    }

    static int write(Map<String, Cell> cells, PrintWriter out) {
        out.println("# Usual share of the free-flow speed (percent) per geohash cell; slots are day type x 24 + hour");
        out.println("# (day types: 0 weekday, 1 Saturday, 2 Sunday), 0 = no data.");
        out.println("# Source: Istanbul Metropolitan Municipality, hourly traffic density data set (IBB Open Data License)");
        int written = 0;
        for (Map.Entry<String, Cell> cell : cells.entrySet()) {
            int[] percent = cell.getValue().percentages();
            if (percent == null) {
                continue;
            }
            StringBuilder line = new StringBuilder(cell.getKey());
            for (int value : percent) {
                line.append(',').append(value);
            }
            out.println(line);
            written++;
        }
        return written;
    }

    /** Vehicle-weighted average speed per slot of one geohash cell. */
    static final class Cell {

        private final double[] weightedSpeed = new double[DayTime.DAY_TYPES * 24];
        private final long[] vehicles = new long[DayTime.DAY_TYPES * 24];

        void add(int slot, double averageSpeed, int count) {
            weightedSpeed[slot] += averageSpeed * count;
            vehicles[slot] += count;
        }

        /** Percent of free flow per slot, or {@code null} when the cell has too little data. */
        int[] percentages() {
            double[] mean = new double[weightedSpeed.length];
            int withData = 0;
            for (int slot = 0; slot < mean.length; slot++) {
                mean[slot] = vehicles[slot] >= MIN_VEHICLES ? weightedSpeed[slot] / vehicles[slot] : Double.NaN;
                if (!Double.isNaN(mean[slot])) {
                    withData++;
                }
            }
            if (withData < MIN_SLOTS) {
                return null;
            }
            double freeFlow = Arrays.stream(mean).filter(value -> !Double.isNaN(value))
                    .boxed().sorted((a, b) -> Double.compare(b, a)).limit(FREE_FLOW_SLOTS)
                    .mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
            if (!(freeFlow > 0)) {
                return null;
            }
            int[] percent = new int[mean.length];
            for (int slot = 0; slot < mean.length; slot++) {
                percent[slot] = Double.isNaN(mean[slot]) ? 0
                        : (int) Math.clamp(Math.round(100 * mean[slot] / freeFlow), SLOWEST_PERCENT, 100);
            }
            return percent;
        }
    }
}
