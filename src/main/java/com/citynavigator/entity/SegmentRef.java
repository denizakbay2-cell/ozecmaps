package com.citynavigator.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.Objects;

/**
 * A road segment identified the stable way: OpenStreetMap way id plus the position of the segment's first node in
 * the way. Graph indexes would change with every import; these do not.
 */
@Embeddable
public class SegmentRef {

    @Column(name = "way_id", nullable = false)
    private long wayId;

    @Column(name = "seq", nullable = false)
    private int sequence;

    protected SegmentRef() {
        // for JPA
    }

    public SegmentRef(long wayId, int sequence) {
        this.wayId = wayId;
        this.sequence = sequence;
    }

    public long getWayId() {
        return wayId;
    }

    public int getSequence() {
        return sequence;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SegmentRef that && wayId == that.wayId && sequence == that.sequence;
    }

    @Override
    public int hashCode() {
        return Objects.hash(wayId, sequence);
    }

    @Override
    public String toString() {
        return "way " + wayId + " #" + sequence;
    }
}
