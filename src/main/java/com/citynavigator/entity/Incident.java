package com.citynavigator.entity;

import com.citynavigator.exception.BusinessRuleException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A road incident (accident, roadworks, congestion, closure) and the road segments it covers. The incident and its
 * segments form one aggregate: they are stored together in one transaction, and routes avoid or slow down on
 * exactly those segments while the incident is active.
 */
@Entity
@Table(name = "incident")
public class Incident {

    public static final int DESCRIPTION_MAX_LENGTH = 300;
    public static final Duration SHORTEST_DURATION = Duration.ofMinutes(15);
    public static final Duration LONGEST_DURATION = Duration.ofDays(30);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;

    @Column(length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lon;

    @Column(length = Place.NAME_MAX_LENGTH)
    private String street;

    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "incident_segment", joinColumns = @JoinColumn(name = "incident_id"))
    private Set<SegmentRef> segments = new HashSet<>();

    @Version
    private long version;

    protected Incident() {
        // for JPA
    }

    private Incident(IncidentType type, String description, double lat, double lon, String street,
                     Set<SegmentRef> segments, Instant reportedAt, Instant endsAt) {
        this.type = type;
        this.status = IncidentStatus.ACTIVE;
        this.description = description;
        this.lat = lat;
        this.lon = lon;
        this.street = street;
        this.segments = new HashSet<>(segments);
        this.reportedAt = reportedAt;
        this.endsAt = endsAt;
    }

    /**
     * Records a new incident.
     *
     * @param location point on the road where it happened (already snapped to the road)
     * @param duration how long it lasts; {@code null} means until someone resolves it
     */
    public static Incident report(IncidentType type, String description, double[] location, String street,
                                  Set<SegmentRef> segments, Instant now, Duration duration) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(now, "now");
        Texts.checkCoordinate(location[0], location[1]);
        if (segments == null || segments.isEmpty()) {
            throw new BusinessRuleException("Olayın etkilediği bir yol parçası bulunamadı");
        }
        if (duration != null && (duration.compareTo(SHORTEST_DURATION) < 0 || duration.compareTo(LONGEST_DURATION) > 0)) {
            throw new BusinessRuleException("Olayın süresi 15 dakika ile 30 gün arasında olmalı");
        }
        return new Incident(type, Texts.optional(description, "Açıklama", DESCRIPTION_MAX_LENGTH),
                location[0], location[1], Texts.optional(street, "Yol adı", Place.NAME_MAX_LENGTH), segments, now,
                duration == null ? null : now.plus(duration));
    }

    /**
     * The road is clear again: routes stop avoiding it. An incident is resolved only once.
     */
    public void resolve(Instant now) {
        if (status != IncidentStatus.ACTIVE) {
            throw new BusinessRuleException("Bu olay zaten kaldırılmış");
        }
        status = IncidentStatus.RESOLVED;
        resolvedAt = Objects.requireNonNull(now, "now");
    }

    /**
     * Whether routes should take the incident into account at that moment.
     */
    public boolean isActiveAt(Instant at) {
        return status == IncidentStatus.ACTIVE && (endsAt == null || at.isBefore(endsAt));
    }

    public Long getId() {
        return id;
    }

    public IncidentType getType() {
        return type;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public String getStreet() {
        return street;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public Optional<Instant> getEndsAt() {
        return Optional.ofNullable(endsAt);
    }

    public Optional<Instant> getResolvedAt() {
        return Optional.ofNullable(resolvedAt);
    }

    public Set<SegmentRef> getSegments() {
        return Set.copyOf(segments);
    }

    public long getVersion() {
        return version;
    }
}
