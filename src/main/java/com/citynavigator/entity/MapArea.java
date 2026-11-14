package com.citynavigator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * The area whose roads were imported: its name, extent, where the map opens and how fresh the data is.
 * There is exactly one row.
 */
@Entity
@Table(name = "map_area")
public class MapArea {

    public static final short SINGLE_ROW = 1;

    @Id
    private short id = SINGLE_ROW;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "min_lat", nullable = false)
    private double minLat;

    @Column(name = "min_lon", nullable = false)
    private double minLon;

    @Column(name = "max_lat", nullable = false)
    private double maxLat;

    @Column(name = "max_lon", nullable = false)
    private double maxLon;

    @Column(name = "center_lat", nullable = false)
    private double centerLat;

    @Column(name = "center_lon", nullable = false)
    private double centerLon;

    @Column(name = "data_timestamp")
    private Instant dataTimestamp;

    @Column(name = "imported_at", nullable = false)
    private Instant importedAt;

    protected MapArea() {
        // for JPA
    }

    /**
     * @param bounds {@code [minLat, minLon, maxLat, maxLon]}
     * @param center {@code [lat, lon]}, inside the bounds
     */
    public MapArea(String name, double[] bounds, double[] center, Instant dataTimestamp, Instant importedAt) {
        this.name = Texts.required(name, "Alan adı", 120);
        Texts.checkCoordinate(bounds[0], bounds[1]);
        Texts.checkCoordinate(bounds[2], bounds[3]);
        Texts.checkCoordinate(center[0], center[1]);
        this.minLat = bounds[0];
        this.minLon = bounds[1];
        this.maxLat = bounds[2];
        this.maxLon = bounds[3];
        this.centerLat = center[0];
        this.centerLon = center[1];
        this.dataTimestamp = dataTimestamp;
        this.importedAt = Objects.requireNonNull(importedAt, "importedAt");
    }

    public String getName() {
        return name;
    }

    public double[] getBounds() {
        return new double[] {minLat, minLon, maxLat, maxLon};
    }

    public double[] getCenter() {
        return new double[] {centerLat, centerLon};
    }

    public Optional<Instant> getDataTimestamp() {
        return Optional.ofNullable(dataTimestamp);
    }

    public Instant getImportedAt() {
        return importedAt;
    }
}
