package com.citynavigator.routing;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Typical congestion by place and time: for each 6-character geohash cell (about 600 × 900 m in Istanbul) and each
 * {@link DayTime}, the share of the free-flow speed that traffic usually keeps. Built from the hourly traffic density
 * data of Istanbul Metropolitan Municipality; places without data are simply not congested.
 */
public final class CongestionProfile {

    public static final CongestionProfile NONE = new CongestionProfile(0, 0, 0, 0, new int[0], new byte[0]);

    /** A 6-character geohash has 15 bits of latitude and 15 of longitude. */
    static final int BITS = 15;
    static final int SLOTS = DayTime.DAY_TYPES * 24;
    private static final String BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz";

    private final int minRow;
    private final int minColumn;
    private final int rows;
    private final int columns;
    private final int[] cellSlot;
    private final byte[] percent;

    private CongestionProfile(int minRow, int minColumn, int rows, int columns, int[] cellSlot, byte[] percent) {
        this.minRow = minRow;
        this.minColumn = minColumn;
        this.rows = rows;
        this.columns = columns;
        this.cellSlot = cellSlot;
        this.percent = percent;
    }

    /**
     * Share of the free-flow speed (0 < factor ≤ 1) usually seen at that place and time, or {@code NaN} if unknown.
     */
    public double factor(double lat, double lon, DayTime when) {
        int row = row(lat) - minRow;
        int column = column(lon) - minColumn;
        if (row < 0 || row >= rows || column < 0 || column >= columns) {
            return Double.NaN;
        }
        int cell = cellSlot[row * columns + column];
        if (cell < 0) {
            return Double.NaN;
        }
        int value = percent[cell * SLOTS + when.slot()] & 0xff;
        return value == 0 ? Double.NaN : value / 100.0;
    }

    public int cellCount() {
        return percent.length / SLOTS;
    }

    public boolean isEmpty() {
        return percent.length == 0;
    }

    static int row(double lat) {
        return (int) Math.floor((lat + 90) / 180 * (1 << BITS));
    }

    static int column(double lon) {
        return (int) Math.floor((lon + 180) / 360 * (1 << BITS));
    }

    /**
     * The {@code [row, column]} of a 6-character geohash: its characters interleave longitude and latitude bits,
     * longitude first.
     */
    static int[] cellOf(String geohash) {
        if (geohash.length() != 6) {
            throw new IllegalArgumentException("a 6-character geohash is needed: " + geohash);
        }
        int row = 0;
        int column = 0;
        boolean longitudeBit = true;
        for (char character : geohash.toCharArray()) {
            int value = BASE32.indexOf(character);
            if (value < 0) {
                throw new IllegalArgumentException("not a geohash: " + geohash);
            }
            for (int bit = 4; bit >= 0; bit--) {
                int set = (value >> bit) & 1;
                if (longitudeBit) {
                    column = column << 1 | set;
                } else {
                    row = row << 1 | set;
                }
                longitudeBit = !longitudeBit;
            }
        }
        return new int[] {row, column};
    }

    /**
     * Reads lines of {@code geohash,p0,…,p71}: one line per cell, the percentage of the free-flow speed for each
     * {@link DayTime#slot()}, 0 where there was no data.
     */
    public static CongestionProfile read(InputStream input) {
        Builder builder = new Builder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(",");
                if (parts.length != SLOTS + 1) {
                    throw new IllegalArgumentException("a profile line needs a geohash and " + SLOTS + " values");
                }
                int[] values = new int[SLOTS];
                for (int i = 0; i < SLOTS; i++) {
                    values[i] = Integer.parseInt(parts[i + 1].strip());
                }
                builder.add(parts[0].strip(), values);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the congestion profile", e);
        }
        return builder.build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Collects cells and lays them out in a rectangle of geohash cells for constant-time lookup.
     */
    public static final class Builder {

        private final List<int[]> cells = new ArrayList<>();
        private final List<byte[]> values = new ArrayList<>();

        private Builder() {
        }

        /**
         * @param percent for each slot, 1–100 percent of the free-flow speed, or 0 for no data
         */
        public Builder add(String geohash, int[] percent) {
            if (percent.length != SLOTS) {
                throw new IllegalArgumentException("one value per day type and hour is needed");
            }
            byte[] encoded = new byte[SLOTS];
            for (int i = 0; i < SLOTS; i++) {
                if (percent[i] < 0 || percent[i] > 100) {
                    throw new IllegalArgumentException("a percentage is between 0 and 100: " + percent[i]);
                }
                encoded[i] = (byte) percent[i];
            }
            cells.add(cellOf(geohash));
            values.add(encoded);
            return this;
        }

        public CongestionProfile build() {
            if (cells.isEmpty()) {
                return NONE;
            }
            int minRow = cells.stream().mapToInt(cell -> cell[0]).min().orElseThrow();
            int maxRow = cells.stream().mapToInt(cell -> cell[0]).max().orElseThrow();
            int minColumn = cells.stream().mapToInt(cell -> cell[1]).min().orElseThrow();
            int maxColumn = cells.stream().mapToInt(cell -> cell[1]).max().orElseThrow();
            int rows = maxRow - minRow + 1;
            int columns = maxColumn - minColumn + 1;
            int[] cellSlot = new int[rows * columns];
            Arrays.fill(cellSlot, -1);
            byte[] percent = new byte[cells.size() * SLOTS];
            for (int i = 0; i < cells.size(); i++) {
                cellSlot[(cells.get(i)[0] - minRow) * columns + cells.get(i)[1] - minColumn] = i;
                System.arraycopy(values.get(i), 0, percent, i * SLOTS, SLOTS);
            }
            return new CongestionProfile(minRow, minColumn, rows, columns, cellSlot, percent);
        }
    }
}
