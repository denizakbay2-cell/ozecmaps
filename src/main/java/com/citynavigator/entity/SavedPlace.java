package com.citynavigator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A place an account keeps: its home, its workplace or one of its saved places. Part of the {@link UserAccount}
 * aggregate; it is created and replaced only through the account.
 */
@Entity
@Table(name = "saved_place")
public class SavedPlace {

    public static final int LABEL_MAX_LENGTH = 200;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private UserAccount account;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SavedPlaceKind kind;

    @Column(nullable = false, length = LABEL_MAX_LENGTH)
    private String label;

    @Column(length = LABEL_MAX_LENGTH)
    private String detail;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    protected SavedPlace() {
        // for JPA
    }

    SavedPlace(UserAccount account, int position, SavedPlaceKind kind, String label, String detail, double lat,
               double lon) {
        if (label == null || label.isBlank() || label.length() > LABEL_MAX_LENGTH
                || detail != null && detail.length() > LABEL_MAX_LENGTH) {
            throw new IllegalArgumentException("a saved place needs a name of at most " + LABEL_MAX_LENGTH
                    + " characters");
        }
        if (!(lat >= -90 && lat <= 90) || !(lon >= -180 && lon <= 180)) {
            throw new IllegalArgumentException("not a valid coordinate: " + lat + ", " + lon);
        }
        this.account = account;
        this.position = position;
        this.kind = kind;
        this.label = label.strip();
        this.detail = detail == null || detail.isBlank() ? null : detail.strip();
        this.lat = lat;
        this.lon = lon;
    }

    public int getPosition() {
        return position;
    }

    public SavedPlaceKind getKind() {
        return kind;
    }

    public String getLabel() {
        return label;
    }

    public String getDetail() {
        return detail;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }
}
