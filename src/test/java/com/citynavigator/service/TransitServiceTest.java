package com.citynavigator.service;

import static com.citynavigator.testsupport.TransitFixture.DESTINATION;
import static com.citynavigator.testsupport.TransitFixture.START;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.citynavigator.dto.TransitLegView;
import com.citynavigator.dto.TransitResponse;
import com.citynavigator.exception.BusinessRuleException;
import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.TransitNetwork;
import com.citynavigator.routing.TransitRouter;
import com.citynavigator.testsupport.TransitFixture;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransitServiceTest {

    /** Monday 16 November 2026, 07:50 in Istanbul. */
    private static final Instant MONDAY_MORNING = Instant.parse("2026-11-16T04:50:00Z");
    private static final Clock CLOCK = Clock.fixed(MONDAY_MORNING, ZoneOffset.UTC);

    private final TransitService service = new TransitService(CLOCK, TransitFixture.network());

    @Test
    void anExtraVehicleIsWorthItWhenItSavesEnoughTime() {
        TransitResponse journey = service.plan(START, DESTINATION, null);

        // ferry and metro arrive half an hour before the bus: worth the change
        assertThat(journey.mode()).isEqualTo("transit");
        assertThat(journey.rides()).isEqualTo(2);
        assertThat(journey.legs()).extracting(TransitLegView::kind)
                .containsExactly("WALK", "FERRY", "WALK", "METRO", "WALK");
        TransitLegView ferry = journey.legs().get(1);
        assertThat(ferry.label()).isEqualTo("Vapur");
        assertThat(ferry.line()).isNull();
        assertThat(ferry.lineName()).isEqualTo("Batı - Doğu");
        assertThat(ferry.from()).isEqualTo("Batı İskelesi");
        assertThat(ferry.to()).isEqualTo("Doğu İskelesi");
        assertThat(ferry.departAt()).isEqualTo(Instant.parse("2026-11-16T05:00:00Z"));
        assertThat(ferry.durationSeconds()).isEqualTo(20 * 60);
        assertThat(ferry.stops()).isEqualTo(1);
        // drawn along its curve, not straight from pier to pier
        assertThat(ferry.geometry()).hasSize(3);
        assertThat(ferry.geometry().get(1)).containsExactly(40.995, 29.015);
        assertThat(ferry.distanceMeters()).isBetween(2_700L, 2_800L);
        TransitLegView metro = journey.legs().get(3);
        assertThat(metro.line()).isEqualTo("M9");
        assertThat(metro.label()).isEqualTo("Metro");
        assertThat(metro.headsign()).isEqualTo("Kuzey");
        TransitLegView first = journey.legs().getFirst();
        assertThat(first.label()).isEqualTo("Yürüyüş");
        assertThat(first.from()).isNull();
        assertThat(first.geometry().getFirst()).containsExactly(START.lat(), START.lon());
        assertThat(journey.arriveAt()).isEqualTo(journey.legs().getLast().arriveAt());
        assertThat(journey.walkMeters()).isEqualTo(journey.legs().stream()
                .filter(leg -> leg.kind().equals("WALK")).mapToLong(TransitLegView::distanceMeters).sum());
        assertThat(journey.source()).isEqualTo("test timetables");
        // the bus is offered too: no change, but later
        assertThat(journey.alternatives()).singleElement().satisfies(bus -> {
            assertThat(bus.rides()).isEqualTo(1);
            assertThat(bus.legs().get(1).line()).isEqualTo("15");
            assertThat(bus.legs().get(1).label()).isEqualTo("Otobüs");
            assertThat(bus.alternatives()).isEmpty();
        });
    }

    @Test
    void aJourneyFreeOfTrafficIsOfferedWhenItIsNotMuchLater() {
        // the 08:00 ferry is gone: the 08:30 one and the metro arrive no earlier than the bus, yet they do not depend
        // on the roads
        TransitResponse journey = service.plan(START, DESTINATION, Instant.parse("2026-11-16T04:59:30Z"));

        assertThat(journey.rides()).isEqualTo(1);
        assertThat(journey.alternatives()).singleElement().satisfies(steady -> assertThat(steady.legs())
                .extracting(TransitLegView::kind).containsExactly("WALK", "FERRY", "WALK", "METRO", "WALK"));
    }

    @Test
    void withOnlyAFewMinutesSavedWalkingWins() {
        // walking takes 23 minutes; a slow ferry would save under three, less than a change is worth
        TransitNetwork.Builder builder = TransitNetwork.builder("test");
        builder.addStop(41.0, 29.0, "Bir");
        builder.addStop(41.0, 29.016, "İki");
        int line = builder.addLine("", "Bir - İki", com.citynavigator.routing.TransitMode.FERRY);
        builder.addPattern(line, "İki", new int[] {0, 1}, new int[] {TransitFixture.at(7, 52),
            TransitFixture.at(8, 10)}, new byte[] {TransitFixture.DAILY});
        TransitService nearby = new TransitService(CLOCK, builder.build());

        TransitResponse journey = nearby.plan(new GeoPoint(41.0, 28.9995), new GeoPoint(41.0, 29.0165), null);

        assertThat(journey.rides()).isZero();
        assertThat(journey.legs()).singleElement().satisfies(walk -> {
            assertThat(walk.kind()).isEqualTo("WALK");
            assertThat(walk.from()).isNull();
            assertThat(walk.to()).isNull();
        });
        assertThat(journey.alternatives()).extracting(TransitResponse::rides).containsExactly(1);
    }

    @Test
    void refusesWhatItCannotPlan() {
        assertThatThrownBy(() -> service.plan(START, new GeoPoint(39.92, 32.85), null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("İstanbul");
        assertThatThrownBy(() -> service.plan(START, START, null))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("aynı yerde");
        // Sunday at eleven at night: nothing runs any more
        assertThatThrownBy(() -> service.plan(START, DESTINATION, Instant.parse("2026-11-15T20:00:00Z")))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("bulunamadı");
        assertThatThrownBy(() -> service.plan(START, DESTINATION, MONDAY_MORNING.plusSeconds(8 * 24 * 3600)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("7 gün");
    }

    @Test
    void theBundledTimetablesCrossTheBosphorusByFerry() {
        TransitNetwork istanbul = TransitService.load();
        TransitService real = new TransitService(CLOCK, istanbul);

        // Kadıköy pier to Beşiktaş pier on a Monday morning
        TransitResponse journey = real.plan(new GeoPoint(40.9917, 29.0240), new GeoPoint(41.0420, 29.0070),
                Instant.parse("2026-11-16T07:00:00Z"));

        assertThat(istanbul.stopCount()).isGreaterThan(10_000);
        assertThat(istanbul.source()).contains("İBB");
        assertThat(journey.rides()).isPositive();
        assertThat(journey.durationSeconds()).isLessThan(90 * 60);
        assertThat(new TransitRouter(istanbul).plan(new GeoPoint(40.9917, 29.0240), new GeoPoint(41.0420, 29.0070),
                0, 10 * 3600)).anySatisfy(found -> assertThat(found.legs()).anySatisfy(leg -> assertThat(leg)
                .isInstanceOfSatisfying(com.citynavigator.routing.TransitJourney.Ride.class, ride -> assertThat(
                        istanbul.lineMode(istanbul.patternLine(ride.pattern())))
                        .isEqualTo(com.citynavigator.routing.TransitMode.FERRY))));
        assertThat(List.of(journey)).isNotEmpty();
    }
}
