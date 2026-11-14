package com.citynavigator.osm;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads the OPL text format of OpenStreetMap data (one object per line, as {@code osmium cat -f opl} writes it):
 * <pre>
 *   n14455442 T x27.1413637 y38.4282601
 *   w3038929 Thighway=residential,name=Plevne%20%Bulvarı Nn1664363213,n8483315080
 * </pre>
 * A country's roads are over a gigabyte of such lines, so they are parsed by hand rather than split into strings.
 */
public final class OplReader {

    private OplReader() {
    }

    /**
     * What to do with each node and way.
     */
    public interface Handler {

        void node(long id, double lat, double lon);

        void way(long id, Map<String, String> tags, long[] nodes);

        /**
         * A node that carries tags, such as a pharmacy or a fuel station; called after {@link #node}. Road networks
         * do not need it.
         */
        default void taggedNode(long id, double lat, double lon, Map<String, String> tags) {
            // nothing by default
        }
    }

    public static void read(BufferedReader reader, Handler handler) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty()) {
                continue;
            }
            switch (line.charAt(0)) {
                case 'n' -> node(line, handler);
                case 'w' -> way(line, handler);
                default -> {
                    // relations and changesets are not needed for routing
                }
            }
        }
    }

    static void node(String line, Handler handler) {
        long id = Long.parseLong(line, 1, fieldEnd(line, 1), 10);
        double lat = Double.NaN;
        double lon = Double.NaN;
        Map<String, String> tags = Map.of();
        int position = fieldEnd(line, 1);
        while (position < line.length()) {
            int start = position + 1;
            int end = fieldEnd(line, start);
            if (start + 1 < end || start < end && line.charAt(start) == 'T') {
                switch (line.charAt(start)) {
                    case 'x' -> lon = Double.parseDouble(line.substring(start + 1, end));
                    case 'y' -> lat = Double.parseDouble(line.substring(start + 1, end));
                    case 'T' -> tags = tags(line, start + 1, end);
                    default -> {
                        // version, timestamp and the like are not needed
                    }
                }
            }
            position = end;
        }
        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            return;
        }
        handler.node(id, lat, lon);
        if (!tags.isEmpty()) {
            handler.taggedNode(id, lat, lon, tags);
        }
    }

    static void way(String line, Handler handler) {
        long id = Long.parseLong(line, 1, fieldEnd(line, 1), 10);
        Map<String, String> tags = Map.of();
        long[] nodes = new long[0];
        int position = fieldEnd(line, 1);
        while (position < line.length()) {
            int start = position + 1;
            int end = fieldEnd(line, start);
            if (start < end && line.charAt(start) == 'T') {
                tags = tags(line, start + 1, end);
            } else if (start < end && line.charAt(start) == 'N') {
                nodes = nodeRefs(line, start + 1, end);
            }
            position = end;
        }
        handler.way(id, tags, nodes);
    }

    private static Map<String, String> tags(String line, int start, int end) {
        Map<String, String> tags = new HashMap<>();
        int position = start;
        while (position < end) {
            int comma = line.indexOf(',', position);
            int tagEnd = comma < 0 || comma > end ? end : comma;
            int equals = line.indexOf('=', position);
            if (equals > position && equals < tagEnd) {
                tags.put(unescape(line.substring(position, equals)), unescape(line.substring(equals + 1, tagEnd)));
            }
            position = tagEnd + 1;
        }
        return tags;
    }

    private static long[] nodeRefs(String line, int start, int end) {
        if (start >= end) {
            return new long[0];
        }
        int count = 1;
        for (int i = start; i < end; i++) {
            if (line.charAt(i) == ',') {
                count++;
            }
        }
        long[] refs = new long[count];
        int position = start;
        for (int i = 0; i < count; i++) {
            int comma = line.indexOf(',', position);
            int refEnd = comma < 0 || comma > end ? end : comma;
            refs[i] = Long.parseLong(line, position + 1, refEnd, 10);
            position = refEnd + 1;
        }
        return refs;
    }

    /** OPL writes unsafe characters as {@code %hex%}, for example a space as {@code %20%}. */
    static String unescape(String text) {
        if (text.indexOf('%') < 0) {
            return text;
        }
        StringBuilder plain = new StringBuilder(text.length());
        int position = 0;
        while (position < text.length()) {
            char character = text.charAt(position);
            int close = character == '%' ? text.indexOf('%', position + 1) : -1;
            if (close > position) {
                plain.appendCodePoint(Integer.parseInt(text, position + 1, close, 16));
                position = close + 1;
            } else {
                plain.append(character);
                position++;
            }
        }
        return plain.toString();
    }

    private static int fieldEnd(String line, int from) {
        int space = line.indexOf(' ', from);
        return space < 0 ? line.length() : space;
    }
}
