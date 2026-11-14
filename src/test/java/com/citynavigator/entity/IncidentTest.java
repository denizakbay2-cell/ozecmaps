package com.citynavigator.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.citynavigator.exception.BusinessRuleException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;

class IncidentTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");
    private static final double[] MODA = {40.9827, 29.0234};
    private static final Set<SegmentRef> SEGMENTS = Set.of(new SegmentRef(10, 0), new SegmentRef(10, 1));

    @Test
    void aReportedIncidentIsActiveUntilItEnds() {
        Incident incident = Incident.report(IncidentType.ROADWORK, " Tek şerit açık ", MODA, "Moda Caddesi",
                SEGMENTS, NOW, Duration.ofHours(2));

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.ACTIVE);
        assertThat(incident.getDescription()).isEqualTo("Tek şerit açık");
        assertThat(incident.getEndsAt()).contains(NOW.plus(Duration.ofHours(2)));
        assertThat(incident.getSegments()).containsExactlyInAnyOrderElementsOf(SEGMENTS);
        assertThat(incident.isActiveAt(NOW.plusSeconds(3_600))).isTrue();
        assertThat(incident.isActiveAt(NOW.plus(Duration.ofHours(2)))).isFalse();
        assertThat(incident.getResolvedAt()).isEmpty();
    }

    @Test
    void withoutADurationItLastsUntilResolvedAndOnlyOnce() {
        Incident incident = Incident.report(IncidentType.CLOSURE, null, MODA, null, SEGMENTS, NOW, null);
        assertThat(incident.getEndsAt()).isEmpty();
        assertThat(incident.isActiveAt(NOW.plus(Duration.ofDays(365)))).isTrue();

        incident.resolve(NOW.plusSeconds(60));
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getResolvedAt()).contains(NOW.plusSeconds(60));
        assertThat(incident.isActiveAt(NOW.plusSeconds(61))).isFalse();
        assertThatThrownBy(() -> incident.resolve(NOW.plusSeconds(120)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("zaten kaldırılmış");
    }

    @Test
    void needsARoadAndASensibleDuration() {
        assertThatThrownBy(() -> Incident.report(IncidentType.ACCIDENT, null, MODA, null, Set.of(), NOW, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("yol parçası");
        assertThatThrownBy(() -> Incident.report(IncidentType.ACCIDENT, null, MODA, null, SEGMENTS, NOW,
                Duration.ofMinutes(5))).isInstanceOf(BusinessRuleException.class).hasMessageContaining("15 dakika");
        assertThatThrownBy(() -> Incident.report(IncidentType.ACCIDENT, null, MODA, null, SEGMENTS, NOW,
                Duration.ofDays(31))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> Incident.report(IncidentType.ACCIDENT, "x".repeat(301), MODA, null, SEGMENTS, NOW,
                null)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("Açıklama");
        assertThatThrownBy(() -> Incident.report(IncidentType.ACCIDENT, null, new double[] {0, 200}, null, SEGMENTS,
                NOW, null)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void eachTypeSaysWhatItDoesToTraffic() {
        assertThat(IncidentType.CLOSURE.closesRoad()).isTrue();
        assertThat(IncidentType.ACCIDENT.closesRoad()).isFalse();
        assertThat(IncidentType.CONGESTION.speedFactor()).isBetween(0.0, 1.0);
        assertThat(IncidentType.ROADWORK.reachMeters()).isPositive();
        assertThat(IncidentType.ROADWORK.label()).isEqualTo("Yol çalışması");
    }

    @Test
    void segmentReferencesCompareByWayAndPosition() {
        assertThat(new SegmentRef(10, 1)).isEqualTo(new SegmentRef(10, 1))
                .hasSameHashCodeAs(new SegmentRef(10, 1))
                .isNotEqualTo(new SegmentRef(10, 2))
                .hasToString("way 10 #1");
    }
}
