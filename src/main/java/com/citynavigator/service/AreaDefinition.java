package com.citynavigator.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * {@code osm/area.properties}: the name of the bundled area and where the map opens.
 */
record AreaDefinition(String name, double centerLat, double centerLon) {

    static AreaDefinition read(InputStream input) {
        Properties properties = new Properties();
        try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read the area definition", e);
        }
        String name = properties.getProperty("name");
        String[] center = properties.getProperty("center", "").split(",");
        if (name == null || name.isBlank() || center.length != 2) {
            throw new IllegalStateException("area.properties needs name=... and center=lat,lon");
        }
        return new AreaDefinition(name.strip(), Double.parseDouble(center[0].strip()),
                Double.parseDouble(center[1].strip()));
    }
}
