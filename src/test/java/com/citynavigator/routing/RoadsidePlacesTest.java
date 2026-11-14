package com.citynavigator.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.citynavigator.routing.RoadsidePlace.Kind;
import com.citynavigator.routing.RoadsidePlaces.Found;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;

class RoadsidePlacesTest {

    /** A road going east along 41° N for about 1.7 km. */
    private static final GeoPoint WEST = new GeoPoint(41.0, 29.0);
    private static final GeoPoint EAST = new GeoPoint(41.0, 29.02);

    private static RoadsidePlace pharmacy(String name, double lat, double lon) {
        return new RoadsidePlace(Kind.PHARMACY, lat, lon, name);
    }

    private static Found found(String name, double along, double offset) {
        return new Found(pharmacy(name, 41.0, 29.0 + along / 84_000), along, offset, true);
    }

    @Test
    void findsThePlacesOfAKindNearTheLineInTheOrderItPassesThem() {
        RoadsidePlaces places = new RoadsidePlaces(List.of(
                pharmacy("Kuzey", 41.0009, 29.015),
                // just south of the line, so in the grid row below it
                pharmacy("Güney", 40.99955, 29.005),
                pharmacy("Uzak", 41.0072, 29.01),
                new RoadsidePlace(Kind.FUEL, 41.0, 29.01, "Benzinlik")));

        List<Found> found = places.along(List.of(WEST, EAST), Kind.PHARMACY, 500);

        assertThat(found).extracting(place -> place.place().name()).containsExactly("Güney", "Kuzey");
        Found south = found.getFirst();
        assertThat(south.alongMeters()).isCloseTo(420, within(5.0));
        assertThat(south.offsetMeters()).isCloseTo(50, within(1.0));
        assertThat(south.onTheRight()).isTrue();
        assertThat(found.get(1).offsetMeters()).isCloseTo(100, within(1.0));
        assertThat(found.get(1).onTheRight()).isFalse();
        assertThat(places.along(List.of(WEST, EAST), Kind.CHARGING, 500)).isEmpty();
    }

    @Test
    void aPlaceTheRoutePassesTwiceIsReachedWhereItIsNearest() {
        RoadsidePlaces places = new RoadsidePlaces(List.of(pharmacy("Arada", 41.00045, 29.01)));
        // east, then back west 200 m further north: the place is 50 m from the way out, 150 m from the way back
        List<GeoPoint> line = List.of(WEST, EAST, new GeoPoint(41.0018, 29.02), new GeoPoint(41.0018, 29.0));

        List<Found> found = places.along(line, Kind.PHARMACY, 500);

        assertThat(found).singleElement().satisfies(place -> {
            assertThat(place.offsetMeters()).isCloseTo(50, within(1.0));
            assertThat(place.alongMeters()).isCloseTo(839, within(5.0));
            assertThat(place.onTheRight()).isFalse();
        });
    }

    @Test
    void whatLiesBehindTheStartOrBeyondTheDestinationIsNotOnTheWay() {
        RoadsidePlaces places = new RoadsidePlaces(List.of(
                pharmacy("Arkada", 41.0, 28.998),
                pharmacy("Başında", 41.0, 28.9996),
                pharmacy("Yanında", 41.0009, 29.0001),
                pharmacy("Ötede", 41.0, 29.022)));

        assertThat(places.along(List.of(WEST, EAST), Kind.PHARMACY, 500)).extracting(place -> place.place().name())
                .containsExactly("Başında", "Yanında");
    }

    @Test
    void spreadsTheChosenPlacesOverTheRouteNearestTheRoadFirst() {
        List<Found> found = List.of(found("a", 100, 50), found("b", 200, 10), found("c", 700, 30),
                found("d", 900, 5));

        // two pieces of 500 m: each keeps the place nearest the road
        assertThat(RoadsidePlaces.spread(found, 1000, 2)).extracting(place -> place.place().name())
                .containsExactly("b", "d");
        // pieces with nothing in them leave room for more places elsewhere
        assertThat(RoadsidePlaces.spread(List.of(found("a", 50, 5), found("b", 100, 40), found("c", 250, 20)), 900, 3))
                .extracting(place -> place.place().name()).containsExactly("a", "b", "c");
        assertThat(RoadsidePlaces.spread(List.of(), 900, 3)).isEmpty();
    }

    @Test
    void placesOfAKindAFewMetresApartCountOnce() {
        Found building = new Found(pharmacy("Moda Eczanesi", 41.0, 29.01), 840, 20, true);
        Found point = new Found(pharmacy("Moda Eczanesi", 41.0001, 29.01), 840, 31, true);
        Found other = new Found(new RoadsidePlace(Kind.FUEL, 41.0, 29.01, "Opet"), 840, 25, true);

        assertThat(RoadsidePlaces.spread(List.of(point, building, other), 1000, 5)).containsExactly(building, other);
    }

    @Test
    void readsTheBundledFormatAndRejectsAnythingElse() throws IOException {
        RoadsidePlaces places = RoadsidePlaces.read(reader("""
                # places along routes: KIND;lat;lon;name
                # OpenStreetMap data of 2026-11-13T21:22:20Z, (c) OpenStreetMap contributors, ODbL
                FUEL;41.00000;29.01000;Opet
                PHARMACY;41.00090;29.01500;Moda Eczanesi

                """));

        assertThat(places.size()).isEqualTo(2);
        assertThat(places.along(List.of(WEST, EAST), Kind.PHARMACY, 200)).extracting(place -> place.place().name())
                .containsExactly("Moda Eczanesi");
        assertThatThrownBy(() -> RoadsidePlaces.read(reader("FUEL;41;29"))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> RoadsidePlaces.read(reader("BAKERY;41;29;Fırın"))).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> RoadsidePlaces.read(reader("FUEL;kuzey;29;Opet"))).isInstanceOf(IOException.class);
    }

    private static BufferedReader reader(String text) {
        return new BufferedReader(new StringReader(text));
    }
}
