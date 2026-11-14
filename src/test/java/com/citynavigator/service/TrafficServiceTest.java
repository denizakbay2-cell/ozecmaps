package com.citynavigator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.citynavigator.dto.TrafficRoadView;
import com.citynavigator.dto.TrafficStatus;
import com.citynavigator.exception.BusinessRuleException;
import com.citynavigator.external.ExternalServiceException;
import com.citynavigator.external.TrafficIndexClient;
import com.citynavigator.routing.CongestionProfile;
import com.citynavigator.routing.DayTime;
import com.citynavigator.routing.RoadTraffic;
import com.citynavigator.testsupport.Geohash;
import com.citynavigator.testsupport.NationalFixture;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TrafficServiceTest {

    /** {@link ServiceFixture#NOW} in Istanbul: a Saturday afternoon. */
    private static final LocalDateTime LOCAL_NOW = LocalDateTime.parse("2026-11-14T15:00");
    private static final DayTime SATURDAY_AFTERNOON = new DayTime(DayTime.SATURDAY, 15);

    @Mock
    private TrafficIndexClient index;
    @Mock
    private ExternalServices external;
    @Mock
    private RoutingEngine engine;
    private final NationalEngine national = new NationalEngine();

    private TrafficService service(CongestionProfile profile) {
        return new TrafficService(ServiceFixture.CLOCK, index, external, engine, national, profile);
    }

    /** The town of {@link ServiceFixture} at 60 % of free flow all week. */
    private static CongestionProfile townAt60Percent() {
        int[] values = new int[72];
        Arrays.fill(values, 60);
        CongestionProfile.Builder profile = CongestionProfile.builder();
        Geohash.cover(40.995, 28.995, 41.01, 29.01).forEach(cell -> profile.add(cell, values));
        return profile.build();
    }

    @Test
    void departuresAroundNowFollowTheLiveIndexAndLaterOnesTheUsualPicture() {
        when(external.enabled()).thenReturn(true);
        when(index.current()).thenReturn(Optional.of(new TrafficIndexClient.Reading(72, LOCAL_NOW)));
        when(index.typical(SATURDAY_AFTERNOON)).thenReturn(60.0);
        TrafficService service = service(townAt60Percent());

        RoadTraffic now = service.forDeparture(ServiceFixture.NOW);
        RoadTraffic soon = service.forDeparture(ServiceFixture.NOW.plus(Duration.ofMinutes(20)));
        RoadTraffic later = service.forDeparture(ServiceFixture.NOW.plus(Duration.ofHours(2)));

        assertThat(now.when()).isEqualTo(SATURDAY_AFTERNOON);
        assertThat(now.liveAdjustment()).isCloseTo(Math.sqrt(29.0 / 41.0), within(1e-9));
        assertThat(now.factor(41.0, 29.001)).isCloseTo(0.6 * Math.sqrt(29.0 / 41.0), within(1e-9));
        assertThat(soon.liveAdjustment()).isEqualTo(now.liveAdjustment());
        assertThat(later.when()).isEqualTo(new DayTime(DayTime.SATURDAY, 17));
        assertThat(later.liveAdjustment()).isEqualTo(1.0);
        assertThat(later.factor(41.0, 29.001)).isCloseTo(0.6, within(1e-9));
    }

    @Test
    void theLiveAdjustmentStaysWithinLimits() {
        assertThat(new TrafficService.Live(99, 10, LOCAL_NOW).adjustment()).isEqualTo(TrafficService.MIN_ADJUSTMENT);
        assertThat(new TrafficService.Live(1, 90, LOCAL_NOW).adjustment()).isEqualTo(TrafficService.MAX_ADJUSTMENT);
        assertThat(new TrafficService.Live(40, 40, LOCAL_NOW).adjustment()).isEqualTo(1.0);
        assertThat(new TrafficService.Live(50, Double.NaN, LOCAL_NOW).adjustment()).isEqualTo(1.0);
    }

    @Test
    void withoutUsableLiveDataTheUsualPictureIsUsed() {
        TrafficService service = service(townAt60Percent());

        when(external.enabled()).thenReturn(false);
        assertThat(service.forDeparture(ServiceFixture.NOW).liveAdjustment()).isEqualTo(1.0);
        verifyNoInteractions(index);

        when(external.enabled()).thenReturn(true);
        when(index.current()).thenReturn(Optional.empty());
        assertThat(service.forDeparture(ServiceFixture.NOW).liveAdjustment()).isEqualTo(1.0);

        when(index.current()).thenReturn(Optional.of(new TrafficIndexClient.Reading(72, LOCAL_NOW.minusHours(2))));
        assertThat(service.forDeparture(ServiceFixture.NOW).liveAdjustment()).isEqualTo(1.0);

        when(index.current()).thenThrow(new ExternalServiceException("down"));
        assertThat(service.forDeparture(ServiceFixture.NOW).liveAdjustment()).isEqualTo(1.0);
    }

    @Test
    void theStatusComparesTheCityWithItsUsualSelf() {
        when(external.enabled()).thenReturn(true);
        when(index.current()).thenReturn(Optional.of(new TrafficIndexClient.Reading(72, LOCAL_NOW)));
        when(index.typical(SATURDAY_AFTERNOON)).thenReturn(59.6);

        TrafficStatus status = service(townAt60Percent()).status();

        assertThat(status.index()).isEqualTo(72);
        assertThat(status.typical()).isEqualTo(60.0);
        assertThat(status.level()).isEqualTo("çok yoğun");
        assertThat(status.comparison()).isEqualTo("normalden yoğun");
        assertThat(status.at()).isEqualTo(LOCAL_NOW);
        assertThat(status.cells()).isPositive();
        assertThat(status.source()).contains("İBB");
    }

    @Test
    void withoutLiveDataTheStatusStillTellsHowMuchIsKnown() {
        when(external.enabled()).thenReturn(false);

        TrafficStatus status = service(CongestionProfile.NONE).status();

        assertThat(status.index()).isNull();
        assertThat(status.typical()).isNull();
        assertThat(status.level()).isNull();
        assertThat(status.cells()).isZero();
    }

    @Test
    void describesLevelsAndComparisonsInTurkish() {
        assertThat(TrafficService.level(10)).isEqualTo("akıcı");
        assertThat(TrafficService.level(30)).isEqualTo("normal");
        assertThat(TrafficService.level(50)).isEqualTo("yoğun");
        assertThat(TrafficService.level(80)).isEqualTo("çok yoğun");
        assertThat(TrafficService.comparison(new TrafficService.Live(30, 45, LOCAL_NOW))).isEqualTo("normalden akıcı");
        assertThat(TrafficService.comparison(new TrafficService.Live(44, 45, LOCAL_NOW))).isEqualTo("her zamanki gibi");
        assertThat(TrafficService.comparison(new TrafficService.Live(44, Double.NaN, LOCAL_NOW))).isNull();
    }

    @Test
    void colorsTheMainRoadsOfAViewFromTheNationalGraph() {
        national.use(NationalFixture.of(ServiceFixture.town()));

        List<TrafficRoadView> roads = service(townAt60Percent()).roadsWithin(40.99, 28.99, 41.01, 29.01);

        // Ana Cadde, cut in two at the junction with Yan Sokak; residential streets stay uncoloured
        assertThat(roads).hasSize(2).allSatisfy(road -> {
            assertThat(road.factor()).isEqualTo(0.6);
            assertThat(road.points()).hasSize(2);
        });
    }

    @Test
    void withoutTheNationalGraphTheDetailedAreaCarriesTheLayer() {
        when(engine.snapshot()).thenReturn(ServiceFixture.snapshot());
        TrafficService service = service(townAt60Percent());

        assertThat(service.roadsWithin(40.99, 28.99, 41.01, 29.01)).hasSize(2);
        assertThat(service.roadsWithin(40.0, 29.0, 40.1, 29.1)).isEmpty();
    }

    @Test
    void refusesInvertedOrTooLargeViews() {
        TrafficService service = service(CongestionProfile.NONE);

        assertThatThrownBy(() -> service.roadsWithin(41.0, 29.0, 40.9, 29.1))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("ters");
        assertThatThrownBy(() -> service.roadsWithin(40.0, 28.0, 41.0, 29.0))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("yakınlaştırın");
    }

    @Test
    void theBundledProfileKnowsIstanbulsRushHours() {
        CongestionProfile profile = TrafficService.bundledProfile();

        assertThat(profile.cellCount()).isGreaterThan(2_000);
        // a cell on the way to the Bosphorus bridge
        assertThat(profile.factor(41.064, 29.108, new DayTime(DayTime.WEEKDAY, 8)))
                .isLessThan(profile.factor(41.064, 29.108, new DayTime(DayTime.WEEKDAY, 3)));
    }
}
