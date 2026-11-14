package com.citynavigator.entity;

import com.citynavigator.exception.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * A named point people can search for: a neighbourhood, a ferry pier, a hospital, a street, a business. Most come
 * from OpenStreetMap ({@code osmRef} set); an administrator can add more. Businesses carry extra details and are
 * shown on the map when zoomed in.
 */
@Entity
@Table(name = "place")
public class Place {

    public static final int NAME_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    @Column(name = "search_key", nullable = false, length = NAME_MAX_LENGTH)
    private String searchKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PlaceCategory category;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    @Column(name = "osm_ref", length = 30, unique = true)
    private String osmRef;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean business;

    @Column(length = 60)
    private String kind;

    @Column(name = "opening_hours", length = 255)
    private String openingHours;

    @Column(length = 60)
    private String phone;

    @Column(length = 255)
    private String website;

    protected Place() {
        // for JPA
    }

    public Place(String name, PlaceCategory category, double lat, double lon, String osmRef, Instant createdAt) {
        this.name = Texts.required(name, "Ad", NAME_MAX_LENGTH);
        this.searchKey = SearchKey.of(this.name);
        if (searchKey.isEmpty()) {
            throw new BusinessRuleException("Ad en az bir harf ya da rakam içermeli");
        }
        this.category = Objects.requireNonNull(category, "category");
        Texts.checkCoordinate(lat, lon);
        this.lat = lat;
        this.lon = lon;
        this.osmRef = osmRef;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Marks the place as a business with the given details.
     */
    public Place asBusiness(PlaceDetails details) {
        this.business = true;
        this.kind = details.kind();
        this.openingHours = details.openingHours();
        this.phone = details.phone();
        this.website = details.website();
        return this;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSearchKey() {
        return searchKey;
    }

    public PlaceCategory getCategory() {
        return category;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public String getOsmRef() {
        return osmRef;
    }

    public boolean isFromOpenStreetMap() {
        return osmRef != null;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isBusiness() {
        return business;
    }

    public PlaceDetails getDetails() {
        return new PlaceDetails(kind, openingHours, phone, website);
    }
}
