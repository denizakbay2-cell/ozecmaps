package com.citynavigator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.citynavigator.entity.Place;
import com.citynavigator.entity.PlaceCategory;
import com.citynavigator.osm.RoadExtract;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OsmPlacesTest {

    @Test
    void mapsTagsToCategories() {
        assertThat(OsmPlaces.categoryOf(Map.of("place", "quarter"))).contains(PlaceCategory.NEIGHBOURHOOD);
        assertThat(OsmPlaces.categoryOf(Map.of("amenity", "ferry_terminal"))).contains(PlaceCategory.TRANSIT);
        assertThat(OsmPlaces.categoryOf(Map.of("railway", "station"))).contains(PlaceCategory.TRANSIT);
        assertThat(OsmPlaces.categoryOf(Map.of("public_transport", "station"))).contains(PlaceCategory.TRANSIT);
        assertThat(OsmPlaces.categoryOf(Map.of("amenity", "pharmacy"))).contains(PlaceCategory.HEALTH);
        assertThat(OsmPlaces.categoryOf(Map.of("tourism", "museum"))).contains(PlaceCategory.CULTURE);
        assertThat(OsmPlaces.categoryOf(Map.of("tourism", "hotel"))).contains(PlaceCategory.HOTEL);
        assertThat(OsmPlaces.categoryOf(Map.of("leisure", "park"))).contains(PlaceCategory.PARK);
        assertThat(OsmPlaces.categoryOf(Map.of("shop", "mall"))).contains(PlaceCategory.SHOPPING);
        assertThat(OsmPlaces.categoryOf(Map.of("historic", "memorial"))).contains(PlaceCategory.CULTURE);
        assertThat(OsmPlaces.categoryOf(Map.of("railway", "proposed"))).isEmpty();
        assertThat(OsmPlaces.categoryOf(Map.of("amenity", "cafe"))).isEmpty();
    }

    @Test
    void readsNamedLandmarksWithAPosition() {
        List<Place> places = OsmPlaces.read(resource("osm/tiny-places.json"), ServiceFixture.NOW);
        assertThat(places).extracting(Place::getName, Place::getCategory, Place::getOsmRef).containsExactly(
                tuple("Kadıköy İskelesi", PlaceCategory.TRANSIT, "node/1"),
                tuple("Moda Parkı", PlaceCategory.PARK, "way/2"),
                tuple("Moda", PlaceCategory.NEIGHBOURHOOD, "node/6"));
    }

    @Test
    void makesOnePlacePerStreetAtTheMiddleOfItsLongestWay() {
        RoadExtract roads = RoadExtract.read(resource("osm/tiny-roads.json"));
        List<Place> streets = OsmPlaces.streets(roads, ServiceFixture.NOW);
        assertThat(streets).singleElement().satisfies(street -> {
            assertThat(street.getName()).isEqualTo("Moda Caddesi");
            assertThat(street.getCategory()).isEqualTo(PlaceCategory.STREET);
            assertThat(street.getLat()).isEqualTo(40.9905);
            assertThat(street.getOsmRef()).isEqualTo("way/10");
        });
    }

    @Test
    void dropsRepeatsAndNearbyTwins() {
        Place pier = place("Kadıköy", PlaceCategory.TRANSIT, 40.9917, 29.0227, "node/1");
        Place sameObject = place("Kadıköy İskele", PlaceCategory.TRANSIT, 40.99, 29.02, "node/1");
        Place twin = place("KADIKÖY", PlaceCategory.TRANSIT, 40.9920, 29.0230, "way/2");
        Place otherKind = place("Kadıköy", PlaceCategory.NEIGHBOURHOOD, 40.9920, 29.0230, "node/3");
        Place farAway = place("Kadıköy", PlaceCategory.TRANSIT, 40.9800, 29.0400, "node/4");

        assertThat(OsmPlaces.withoutDuplicates(List.of(pier, sameObject, twin, otherKind, farAway)))
                .containsExactly(pier, otherKind, farAway);
    }

    private static Place place(String name, PlaceCategory category, double lat, double lon, String ref) {
        return new Place(name, category, lat, lon, ref, ServiceFixture.NOW);
    }

    private static InputStream resource(String name) {
        return OsmPlacesTest.class.getClassLoader().getResourceAsStream(name);
    }
}
