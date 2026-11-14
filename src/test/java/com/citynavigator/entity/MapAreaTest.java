package com.citynavigator.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.citynavigator.exception.BusinessRuleException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MapAreaTest {

    @Test
    void keepsTheAreaAndHandsOutCopies() {
        Instant now = Instant.parse("2026-11-14T12:00:00Z");
        MapArea area = new MapArea("Kadıköy", new double[] {40.95, 29.01, 41.01, 29.11}, new double[] {40.98, 29.03},
                null, now);
        assertThat(area.getName()).isEqualTo("Kadıköy");
        assertThat(area.getBounds()).containsExactly(40.95, 29.01, 41.01, 29.11);
        area.getCenter()[0] = 0;
        assertThat(area.getCenter()).containsExactly(40.98, 29.03);
        assertThat(area.getDataTimestamp()).isEmpty();
        assertThat(area.getImportedAt()).isEqualTo(now);
    }

    @Test
    void rejectsAnUnnamedOrImpossibleArea() {
        Instant now = Instant.EPOCH;
        assertThatThrownBy(() -> new MapArea("", new double[] {0, 0, 1, 1}, new double[] {0, 0}, null, now))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> new MapArea("X", new double[] {0, 0, 100, 1}, new double[] {0, 0}, null, now))
                .isInstanceOf(BusinessRuleException.class);
    }
}
