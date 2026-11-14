package com.citynavigator.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrafficConditionsTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @Test
    void aClosureStopsCarsAndBicyclesButNotWalkers() {
        TrafficConditions traffic = TrafficConditions.NONE.with(TrafficConditions.Restriction.closure(1, null),
                List.of(5));
        assertThat(traffic.speedFactor(5, TravelMode.CAR, NOW)).isZero();
        assertThat(traffic.speedFactor(5, TravelMode.BICYCLE, NOW)).isZero();
        assertThat(traffic.speedFactor(5, TravelMode.FOOT, NOW)).isEqualTo(1);
        assertThat(traffic.speedFactor(6, TravelMode.CAR, NOW)).isEqualTo(1);
    }

    @Test
    void slowdownsOnlyAffectCarsAndTheStrongestWins() {
        TrafficConditions traffic = TrafficConditions.NONE
                .with(TrafficConditions.Restriction.slowdown(1, 0.5, null), List.of(5, 6))
                .with(TrafficConditions.Restriction.slowdown(2, 0.3, null), List.of(6));
        assertThat(traffic.speedFactor(5, TravelMode.CAR, NOW)).isEqualTo(0.5);
        assertThat(traffic.speedFactor(6, TravelMode.CAR, NOW)).isEqualTo(0.3);
        assertThat(traffic.speedFactor(6, TravelMode.BICYCLE, NOW)).isEqualTo(1);
        assertThat(traffic.incidentsOn(6, NOW)).containsExactly(1L, 2L);
        assertThat(traffic.restrictedSegmentCount()).isEqualTo(2);
    }

    @Test
    void anExpiredRestrictionNoLongerCounts() {
        TrafficConditions traffic = TrafficConditions.NONE.with(
                TrafficConditions.Restriction.closure(1, NOW.plusSeconds(60)), List.of(5));
        assertThat(traffic.speedFactor(5, TravelMode.CAR, NOW)).isZero();
        assertThat(traffic.speedFactor(5, TravelMode.CAR, NOW.plusSeconds(60))).isEqualTo(1);
        assertThat(traffic.incidentsOn(5, NOW.plusSeconds(61))).isEmpty();
    }

    @Test
    void removingAnIncidentLeavesTheOthersAndTheOriginalUntouched() {
        TrafficConditions both = TrafficConditions.NONE
                .with(TrafficConditions.Restriction.closure(1, null), List.of(5))
                .with(TrafficConditions.Restriction.slowdown(2, 0.5, null), List.of(5, 7));
        TrafficConditions withoutClosure = both.without(1);

        assertThat(withoutClosure.speedFactor(5, TravelMode.CAR, NOW)).isEqualTo(0.5);
        assertThat(both.speedFactor(5, TravelMode.CAR, NOW)).isZero();
        assertThat(withoutClosure.without(2).restrictedSegmentCount()).isZero();
    }

    @Test
    void reportingAnIncidentAgainReplacesItsSegments() {
        TrafficConditions traffic = TrafficConditions.NONE
                .with(TrafficConditions.Restriction.closure(1, null), List.of(5))
                .with(TrafficConditions.Restriction.closure(1, null), List.of(6));
        assertThat(traffic.speedFactor(5, TravelMode.CAR, NOW)).isEqualTo(1);
        assertThat(traffic.speedFactor(6, TravelMode.CAR, NOW)).isZero();
    }

    @Test
    void aSlowdownNeedsAFactorBetweenZeroAndOne() {
        assertThatThrownBy(() -> TrafficConditions.Restriction.slowdown(1, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrafficConditions.Restriction.slowdown(1, 1.5, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
