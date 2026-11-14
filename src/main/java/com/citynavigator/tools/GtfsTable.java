package com.citynavigator.tools;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Reads one file of a GTFS feed as İBB publishes them: UTF-8 or Windows-1254 (Turkish), commas or semicolons,
 * sometimes zipped. The header names the columns; rows come one at a time, so a file of millions of rows needs no
 * memory.
 */
final class GtfsTable {

    private static final Charset TURKISH = Charset.forName("windows-1254");

    private final Map<String, Integer> columns = new HashMap<>();

    private GtfsTable() {
    }

    /** What to do with each row. */
    interface Rows {
        void row(Row row);
    }

    /** One row; a column the file does not have reads as empty. */
    static final class Row {

        private final GtfsTable table;
        private final List<String> values;

        private Row(GtfsTable table, List<String> values) {
            this.table = table;
            this.values = values;
        }

        String get(String column) {
            Integer index = table.columns.get(column);
            return index == null || index >= values.size() ? "" : values.get(index).strip();
        }
    }

    /**
     * Reads {@code name.txt} of the feed, or the only entry of {@code name.zip}; does nothing when neither exists.
     */
    static void read(Path feed, String name, Rows rows) throws IOException {
        Path text = feed.resolve(name + ".txt");
        Path zip = feed.resolve(name + ".zip");
        if (Files.exists(text)) {
            byte[] start = new byte[1 << 16];
            int length;
            try (InputStream in = Files.newInputStream(text)) {
                length = in.readNBytes(start, 0, start.length);
            }
            Charset charset = utf8(start, length) ? StandardCharsets.UTF_8 : TURKISH;
            try (BufferedReader reader = Files.newBufferedReader(text, charset)) {
                read(reader, rows);
            }
        } else if (Files.exists(zip)) {
            try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
                ZipEntry entry = in.getNextEntry();
                if (entry != null) {
                    read(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 1 << 16), rows);
                }
            }
        }
    }

    static void read(BufferedReader reader, Rows rows) throws IOException {
        String header = reader.readLine();
        if (header == null) {
            return;
        }
        if (header.startsWith("﻿")) {
            header = header.substring(1);
        }
        char separator = header.indexOf(';') > header.indexOf(',') ? ';' : ',';
        GtfsTable table = new GtfsTable();
        List<String> names = split(header, separator);
        for (int i = 0; i < names.size(); i++) {
            table.columns.putIfAbsent(names.get(i).strip(), i);
        }
        String line;
        while ((line = reader.readLine()) != null) {
            if (!line.isBlank()) {
                rows.row(new Row(table, split(line, separator)));
            }
        }
    }

    /** Whether the bytes read as UTF-8 (the last few may be a character cut in half). */
    private static boolean utf8(byte[] bytes, int length) {
        try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, 0, Math.max(0, length < bytes.length ? length : length - 4)));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    /** Splits a line at the separator, keeping quoted fields (with {@code ""} for a quote) together. */
    static List<String> split(String line, char separator) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (quoted) {
                if (character == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (character == '"') {
                    quoted = false;
                } else {
                    field.append(character);
                }
            } else if (character == '"') {
                quoted = true;
            } else if (character == separator) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(character);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    /** For tests: reads a table given as text. */
    static void read(String text, Rows rows) throws IOException {
        read(new BufferedReader(new InputStreamReader(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8)), rows);
    }
}
