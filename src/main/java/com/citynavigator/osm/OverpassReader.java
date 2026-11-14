package com.citynavigator.osm;

import jakarta.json.Json;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import jakarta.json.stream.JsonParser;
import java.io.InputStream;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Streams an Overpass API JSON document element by element, so a large extract never sits in memory as one tree.
 */
public final class OverpassReader {

    private OverpassReader() {
    }

    /**
     * Hands every element to the consumer and returns the time the data was taken from OpenStreetMap, if stated.
     */
    public static Optional<Instant> read(InputStream input, Consumer<OsmElement> consumer) {
        Instant timestamp = null;
        try (JsonParser parser = Json.createParser(input)) {
            while (parser.hasNext()) {
                if (parser.next() != JsonParser.Event.KEY_NAME) {
                    continue;
                }
                String key = parser.getString();
                if ("osm3s".equals(key) && parser.next() == JsonParser.Event.START_OBJECT) {
                    timestamp = timestampOf(parser.getObject().getString("timestamp_osm_base", null));
                } else if ("elements".equals(key) && parser.next() == JsonParser.Event.START_ARRAY) {
                    while (parser.next() == JsonParser.Event.START_OBJECT) {
                        consumer.accept(toElement(parser.getObject()));
                    }
                }
            }
        }
        return Optional.ofNullable(timestamp);
    }

    static OsmElement toElement(JsonObject json) {
        double lat = Double.NaN;
        double lon = Double.NaN;
        JsonObject position = json.containsKey("lat") ? json : json.getJsonObject("center");
        if (position != null && position.containsKey("lat") && position.containsKey("lon")) {
            lat = position.getJsonNumber("lat").doubleValue();
            lon = position.getJsonNumber("lon").doubleValue();
        }
        List<Long> nodes = json.containsKey("nodes")
                ? json.getJsonArray("nodes").getValuesAs(JsonNumber.class).stream().map(JsonNumber::longValue).toList()
                : List.of();
        Map<String, String> tags = new LinkedHashMap<>();
        JsonObject jsonTags = json.getJsonObject("tags");
        if (jsonTags != null) {
            jsonTags.forEach((key, value) -> tags.put(key, textOf(value)));
        }
        return new OsmElement(json.getString("type"), json.getJsonNumber("id").longValue(), lat, lon, nodes, tags);
    }

    private static String textOf(JsonValue value) {
        return value instanceof JsonString text ? text.getString() : value.toString();
    }

    private static Instant timestampOf(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException notATimestamp) {
            return null;
        }
    }
}
