package com.citynavigator.routing;

import static com.citynavigator.testsupport.TransitFixture.A;
import static com.citynavigator.testsupport.TransitFixture.B;
import static com.citynavigator.testsupport.TransitFixture.C;
import static com.citynavigator.testsupport.TransitFixture.D;
import static com.citynavigator.testsupport.TransitFixture.DESTINATION;
import static com.citynavigator.testsupport.TransitFixture.E;
import static com.citynavigator.testsupport.TransitFixture.START;
import static com.citynavigator.testsupport.TransitFixture.at;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.citynavigator.routing.TransitJourney.Ride;
import com.citynavigator.routing.TransitJourney.Walk;
import com.citynavigator.testsupport.TransitFixture;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransitRouterTest {

    private static final int MONDAY = 0;
    private static final int TUESDAY = 1;
    private static final int SUNDAY = 6;
    /** From the start to the ferry pier, and from the last stop to the destination: 56 m. */
    private static final int SHORT_WALK = TransitNetwork.walkSeconds(GeoMath.distance(START, new GeoPoint(41.0, 29.0)));

    private final TransitNetwork network = TransitFixture.network();
    private final TransitRouter router = new TransitRouter(network);

    @Test
    void offersTheFastestJourneyForEachNumberOfRides() {
        List<TransitJourney> journeys = router.plan(START, DESTINATION, MONDAY, at(7, 50));

        assertThat(journeys).extracting(TransitJourney::rides).containsExactly(1, 2);
        TransitJourney bus = journeys.getFirst();
        assertThat(bus.legs()).hasSize(3);
        assertThat(bus.legs().get(1)).isInstanceOfSatisfying(Ride.class, ride -> {
            assertThat(network.patternStop(ride.pattern(), ride.boardIndex())).isEqualTo(C);
            assertThat(ride.departure()).isEqualTo(at(8, 5));
        });
        assertThat(bus.arrival()).isEqualTo(at(9, 0) + SHORT_WALK);

        // the ferry, a walk to the metro and the metro: half an hour earlier
        TransitJourney ferry = journeys.get(1);
        assertThat(ferry.legs()).hasSize(5);
        assertThat(ferry.legs().get(0)).isEqualTo(new Walk(-1, A, at(8, 0) - TransitRouter.BOARD_SECONDS - SHORT_WALK,
                at(8, 0) - TransitRouter.BOARD_SECONDS, ferry.legs().get(0) instanceof Walk walk ? walk.meters() : 0));
        assertThat(ferry.legs().get(1)).isInstanceOfSatisfying(Ride.class, ride -> {
            assertThat(ride.departure()).isEqualTo(at(8, 0));
            assertThat(ride.arrival()).isEqualTo(at(8, 20));
        });
        assertThat(ferry.legs().get(2)).isInstanceOfSatisfying(Walk.class, walk -> {
            assertThat(walk.fromStop()).isEqualTo(B);
            assertThat(walk.toStop()).isEqualTo(E);
            assertThat(walk.departure()).isEqualTo(at(8, 20));
        });
        assertThat(ferry.legs().get(3)).isInstanceOfSatisfying(Ride.class,
                ride -> assertThat(ride.departure()).isEqualTo(at(8, 25)));
        assertThat(ferry.legs().get(4)).isInstanceOfSatisfying(Walk.class, walk -> {
            assertThat(walk.fromStop()).isEqualTo(D);
            assertThat(walk.toStop()).isEqualTo(-1);
        });
        // the journey leaves just in time for the ferry
        assertThat(ferry.departure()).isEqualTo(at(8, 0) - TransitRouter.BOARD_SECONDS - SHORT_WALK);
        assertThat(ferry.arrival()).isEqualTo(at(8, 30) + SHORT_WALK);
    }

    @Test
    void aVehicleIsCaughtOnlyWithAMinuteToSpare() {
        // reaching the pier at 07:59:54 leaves no minute before the 08:00 ferry; the 08:30 one is no faster than the bus
        List<TransitJourney> journeys = router.plan(START, DESTINATION, MONDAY, at(8, 0) - SHORT_WALK - 59);

        assertThat(journeys).singleElement().satisfies(journey -> assertThat(journey.rides()).isEqualTo(1));
    }

    @Test
    void tripsRunOnlyOnTheirDays() {
        // on Sundays the first ferry leaves at ten: the bus is the only journey
        assertThat(router.plan(START, DESTINATION, SUNDAY, at(7, 50))).singleElement()
                .satisfies(journey -> assertThat(journey.arrival()).isEqualTo(at(9, 0) + SHORT_WALK));
    }

    @Test
    void earlyInTheMorningTheNightTripsOfTheDayBeforeCount() {
        // Monday's 24:30 bus runs at half past midnight on Tuesday
        List<TransitJourney> journeys = router.plan(START, DESTINATION, TUESDAY, at(0, 10));

        assertThat(journeys).singleElement().satisfies(journey -> {
            assertThat(journey.legs().get(1)).isInstanceOfSatisfying(Ride.class,
                    ride -> assertThat(ride.departure()).isEqualTo(at(0, 30)));
            assertThat(journey.arrival()).isEqualTo(at(1, 25) + SHORT_WALK);
        });
        // the night bus runs only after Mondays: on a Wednesday night the first journeys start at eight
        assertThat(router.plan(START, DESTINATION, 2, at(2, 0))).extracting(TransitJourney::departure)
                .containsExactly(at(8, 5) - TransitRouter.BOARD_SECONDS - TransitNetwork.walkSeconds(
                        GeoMath.distance(START, network.stop(C))), at(8, 0) - TransitRouter.BOARD_SECONDS - SHORT_WALK);
    }

    @Test
    void searchesCanLeaveSomeKindsOfVehicleOut() {
        // without buses the ferry and the metro remain; without the ferry, only the bus
        assertThat(router.plan(START, DESTINATION, MONDAY, at(7, 50), java.util.EnumSet.of(TransitMode.FERRY,
                TransitMode.METRO))).extracting(TransitJourney::rides).containsExactly(2);
        assertThat(router.plan(START, DESTINATION, MONDAY, at(7, 50), java.util.EnumSet.of(TransitMode.BUS,
                TransitMode.METRO))).extracting(TransitJourney::rides).containsExactly(1);
    }

    @Test
    void walkingAllTheWayIsAJourneyToo() {
        GeoPoint nearby = new GeoPoint(41.0, 29.006);

        List<TransitJourney> journeys = router.plan(START, nearby, MONDAY, at(7, 50));

        assertThat(journeys.getFirst().rides()).isZero();
        assertThat(journeys.getFirst().legs()).singleElement().isInstanceOfSatisfying(Walk.class, walk -> {
            assertThat(walk.fromStop()).isEqualTo(-1);
            assertThat(walk.toStop()).isEqualTo(-1);
        });
        // nothing at all reaches a place far from every stop
        assertThat(router.plan(START, new GeoPoint(41.2, 29.5), MONDAY, at(7, 50))).isEmpty();
    }

    @Test
    void nearbyStopsAreLinkedByWalks() {
        assertThat(network.stopsWithin(START, 150)).containsExactlyInAnyOrder(A, C);
        assertThat(network.covers(new GeoPoint(41.03, 29.0), 2_000)).isTrue();
        assertThat(network.covers(new GeoPoint(41.2, 29.0), 2_000)).isFalse();
        int from = network.walkStart[B];
        int to = network.walkStart[B + 1];
        assertThat(java.util.Arrays.copyOfRange(network.walkTo, from, to)).containsExactly(E);
        assertThat(network.walkSeconds[from]).isEqualTo(TransitNetwork.walkSeconds(
                GeoMath.distance(network.stop(B), network.stop(E))));
    }

    @Test
    void theBuilderRefusesTimetablesTheRouterCannotUse() {
        TransitNetwork.Builder builder = TransitNetwork.builder("test");
        builder.addStop(41.0, 29.0, "Bir");
        builder.addStop(41.0, 29.01, "İki");
        int line = builder.addLine("1", "Bir - İki", TransitMode.BUS);

        assertThatThrownBy(() -> builder.addPattern(line, "", new int[] {0, 1}, new int[] {100, 50},
                new byte[] {1})).hasMessageContaining("back in time");
        assertThatThrownBy(() -> builder.addPattern(line, "", new int[] {0, 1}, new int[] {100, 500, 200, 300},
                new byte[] {1, 1})).hasMessageContaining("overtakes");
        assertThatThrownBy(() -> builder.addPattern(line, "", new int[] {0}, new int[] {100},
                new byte[] {1})).hasMessageContaining("two stops");
        assertThatThrownBy(() -> builder.addPattern(line, "", new int[] {0, 7}, new int[] {100, 200},
                new byte[] {1})).hasMessageContaining("no stop");
        assertThatThrownBy(() -> builder.addPattern(3, "", new int[] {0, 1}, new int[] {100, 200},
                new byte[] {1})).hasMessageContaining("no line");
        assertThatThrownBy(() -> builder.addPattern(line, "", new int[] {0, 1}, new int[] {100, 200},
                new byte[] {1}, new TransitNetwork.Shape(new double[] {41, 41}, new double[] {29, 29.01}, new int[] {0})))
                .hasMessageContaining("every stop");
        assertThatThrownBy(() -> new TransitNetwork.Shape(new double[] {41, 41}, new double[] {29, 29.01},
                new int[] {1, 0})).hasMessageContaining("in order");
        assertThatThrownBy(() -> new TransitNetwork.Shape(new double[] {41}, new double[] {29}, new int[] {0}))
                .hasMessageContaining("two points");
    }

    @Test
    void ridesFollowTheLineOfTheirVehicles() {
        // the ferry has its curve; the bus is known only by its stops
        assertThat(network.rideLine(0, 0, 1)).containsExactly(new GeoPoint(41.0, 29.0), new GeoPoint(40.995, 29.015),
                new GeoPoint(41.0, 29.03));
        assertThat(network.rideLine(1, 0, 1)).containsExactly(network.stop(C), network.stop(D));
        assertThat(network.shape(1)).isNull();
    }

    @Test
    void theFileKeepsEverything() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        TransitNetworkFile.write(network, bytes);

        TransitNetwork read = TransitNetworkFile.read(new ByteArrayInputStream(bytes.toByteArray()));

        assertThat(read.source()).isEqualTo("test timetables");
        assertThat(read.stopCount()).isEqualTo(5);
        assertThat(read.stopName(A)).isEqualTo("Batı İskelesi");
        assertThat(read.stop(D)).isEqualTo(network.stop(D));
        assertThat(read.lineName(2)).isEqualTo("M9");
        assertThat(read.lineMode(0)).isEqualTo(TransitMode.FERRY);
        assertThat(read.patternHeadsign(1)).isEqualTo("Kuzey");
        assertThat(read.time(1, 1, 1)).isEqualTo(at(25, 25));
        assertThat(read.runs(0, 3, SUNDAY)).isTrue();
        assertThat(read.runs(0, 3, MONDAY)).isFalse();
        assertThat(read.rideLine(0, 0, 1)).isEqualTo(network.rideLine(0, 0, 1));
        assertThat(read.shape(2)).isNull();
        assertThat(new TransitRouter(read).plan(START, DESTINATION, MONDAY, at(7, 50)))
                .isEqualTo(router.plan(START, DESTINATION, MONDAY, at(7, 50)));
        assertThatThrownBy(() -> TransitNetworkFile.read(new ByteArrayInputStream(new byte[] {31, -117, 8, 0})))
                .isInstanceOf(IOException.class);
    }
}
