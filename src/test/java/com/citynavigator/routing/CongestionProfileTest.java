package com.citynavigator.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CongestionProfileTest {

    /** Geohash sxk9w7 spans 41.06140–41.06689 N, 29.10278–29.11377 E (on the Bosphorus bridge approach). */
    private static final double INSIDE_LAT = 41.064;
    private static final double INSIDE_LON = 29.108;

    @Test
    void decodesGeohashCellsLongitudeBitFirst() {
        int[] cell = CongestionProfile.cellOf("sxk9w7");

        assertThat(cell[0]).isEqualTo(CongestionProfile.row(INSIDE_LAT));
        assertThat(cell[1]).isEqualTo(CongestionProfile.column(INSIDE_LON));
        assertThat(CongestionProfile.row(41.0614)).isEqualTo(cell[0] - 1);
        assertThat(CongestionProfile.column(29.1138)).isEqualTo(cell[1] + 1);
        assertThatThrownBy(() -> CongestionProfile.cellOf("sxk9w")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CongestionProfile.cellOf("sxk9wa")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsTheUsualShareOfFreeFlowByPlaceDayTypeAndHour() {
        int[] values = new int[CongestionProfile.SLOTS];
        Arrays.fill(values, 100);
        values[new DayTime(DayTime.WEEKDAY, 8).slot()] = 55;
        values[new DayTime(DayTime.SATURDAY, 18).slot()] = 50;
        values[new DayTime(DayTime.SUNDAY, 3).slot()] = 0;
        String text = "# comment\n\nsxk9w7," + String.join(",", Arrays.stream(values).mapToObj(String::valueOf)
                .toList()) + "\n";

        CongestionProfile profile = CongestionProfile.read(stream(text));

        assertThat(profile.cellCount()).isEqualTo(1);
        assertThat(profile.isEmpty()).isFalse();
        assertThat(profile.factor(INSIDE_LAT, INSIDE_LON, new DayTime(DayTime.WEEKDAY, 8))).isEqualTo(0.55);
        assertThat(profile.factor(INSIDE_LAT, INSIDE_LON, new DayTime(DayTime.SATURDAY, 18))).isEqualTo(0.5);
        assertThat(profile.factor(INSIDE_LAT, INSIDE_LON, new DayTime(DayTime.WEEKDAY, 3))).isEqualTo(1.0);
        assertThat(profile.factor(INSIDE_LAT, INSIDE_LON, new DayTime(DayTime.SUNDAY, 3))).isNaN();
        assertThat(profile.factor(41.07, INSIDE_LON, new DayTime(DayTime.WEEKDAY, 8))).isNaN();
        assertThat(profile.factor(-INSIDE_LAT, INSIDE_LON, new DayTime(DayTime.WEEKDAY, 8))).isNaN();
    }

    @Test
    void refusesLinesWithTheWrongNumberOfValues() {
        assertThatThrownBy(() -> CongestionProfile.read(stream("sxk9w7,1,2,3\n")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anEmptyProfileKnowsNothing() {
        assertThat(CongestionProfile.NONE.isEmpty()).isTrue();
        assertThat(CongestionProfile.NONE.cellCount()).isZero();
        assertThat(CongestionProfile.NONE.factor(41, 29, new DayTime(DayTime.WEEKDAY, 8))).isNaN();
        assertThat(CongestionProfile.read(stream("# nothing\n")).isEmpty()).isTrue();
    }

    @Test
    void roadTrafficScalesTheUsualPictureByTheLiveAdjustmentWithinLimits() {
        int[] values = new int[CongestionProfile.SLOTS];
        Arrays.fill(values, 60);
        CongestionProfile profile = CongestionProfile.builder().add("sxk9w7", values).build();
        DayTime rush = new DayTime(DayTime.WEEKDAY, 18);

        assertThat(new RoadTraffic(profile, rush, 1).factor(INSIDE_LAT, INSIDE_LON)).isCloseTo(0.6, within(1e-9));
        assertThat(new RoadTraffic(profile, rush, 0.5).factor(INSIDE_LAT, INSIDE_LON)).isCloseTo(0.3, within(1e-9));
        assertThat(new RoadTraffic(profile, rush, 2).factor(INSIDE_LAT, INSIDE_LON)).isEqualTo(1.0);
        assertThat(new RoadTraffic(profile, rush, 0.01).factor(INSIDE_LAT, INSIDE_LON)).isEqualTo(RoadTraffic.SLOWEST);
        assertThat(new RoadTraffic(profile, rush, 0.5).factor(40, 29)).isEqualTo(1.0);
        assertThat(new RoadTraffic(profile, rush, 1).covers(INSIDE_LAT, INSIDE_LON)).isTrue();
        assertThat(new RoadTraffic(profile, rush, 1).covers(40, 29)).isFalse();
        assertThat(RoadTraffic.NONE.factor(INSIDE_LAT, INSIDE_LON)).isEqualTo(1.0);
    }

    @Test
    void tellsWeekdaysSaturdaysAndSundaysApart() {
        assertThat(DayTime.of(LocalDateTime.parse("2026-11-16T08:30"))).isEqualTo(new DayTime(DayTime.WEEKDAY, 8));
        assertThat(DayTime.of(LocalDateTime.parse("2026-11-20T23:59"))).isEqualTo(new DayTime(DayTime.WEEKDAY, 23));
        assertThat(DayTime.of(LocalDateTime.parse("2026-11-14T18:00")).slot()).isEqualTo(24 + 18);
        assertThat(DayTime.of(LocalDateTime.parse("2026-11-15T00:10")).slot()).isEqualTo(48);
        assertThatThrownBy(() -> new DayTime(3, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DayTime(DayTime.WEEKDAY, 24)).isInstanceOf(IllegalArgumentException.class);
    }

    private static ByteArrayInputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }
}
