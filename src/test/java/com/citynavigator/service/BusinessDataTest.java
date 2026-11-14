package com.citynavigator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.citynavigator.entity.Place;
import com.citynavigator.entity.PlaceCategory;
import com.citynavigator.entity.PlaceDetails;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BusinessDataTest {

    @Test
    void knowsTheCommonKindsInTurkishAndTreatsOtherShopsAsShops() {
        assertThat(OsmKinds.businessOf(Map.of("amenity", "pharmacy")))
                .contains(new OsmKinds.Kind(PlaceCategory.HEALTH, "Eczane"));
        assertThat(OsmKinds.businessOf(Map.of("shop", "supermarket")))
                .contains(new OsmKinds.Kind(PlaceCategory.GROCERY, "Süpermarket"));
        assertThat(OsmKinds.businessOf(Map.of("shop", "music")))
                .contains(new OsmKinds.Kind(PlaceCategory.SHOP, "Mağaza"));
        assertThat(OsmKinds.businessOf(Map.of("craft", "carpenter")))
                .contains(new OsmKinds.Kind(PlaceCategory.SERVICE, "Usta / atölye"));
        assertThat(OsmKinds.businessOf(Map.of("amenity", "townhall"))).isEmpty();
        assertThat(OsmKinds.businessOf(Map.of("amenity", "unknown", "tourism", "hotel")))
                .contains(new OsmKinds.Kind(PlaceCategory.HOTEL, "Otel"));
    }

    @Test
    void readsNamedBusinessesWithCleanedDetails() {
        List<Place> businesses = OsmPlaces.readBusinesses(resource("osm/tiny-businesses.json"), ServiceFixture.NOW);

        assertThat(businesses).extracting(Place::getName, Place::getCategory).containsExactly(
                tuple("Kahve Durağı", PlaceCategory.FOOD),
                tuple("Migros", PlaceCategory.GROCERY),
                tuple("Migros", PlaceCategory.GROCERY),
                tuple("Plak Dükkanı", PlaceCategory.SHOP),
                tuple("Ahşap Atölyesi", PlaceCategory.SERVICE),
                tuple("Otel Moda", PlaceCategory.HOTEL));
        assertThat(businesses).allMatch(Place::isBusiness);
        PlaceDetails cafe = businesses.getFirst().getDetails();
        assertThat(cafe).isEqualTo(new PlaceDetails("Kafe", "Mo-Su 08:00-23:00", "+90 216 555 11 22",
                "https://kahve.example"));
        assertThat(businesses.get(1).getDetails().website()).isNull();
    }

    @Test
    void twoBranchesOfAChainAStreetApartAreTwoBusinesses() {
        List<Place> businesses = OsmPlaces.readBusinesses(resource("osm/tiny-businesses.json"), ServiceFixture.NOW);
        List<Place> kept = OsmPlaces.withoutDuplicates(businesses);
        assertThat(kept).filteredOn(place -> place.getName().equals("Migros")).hasSize(2);

        Place copy = new Place("Kahve Durağı", PlaceCategory.FOOD, 40.99001, 29.02701, "way/99", ServiceFixture.NOW)
                .asBusiness(PlaceDetails.NONE);
        assertThat(OsmPlaces.withoutDuplicates(List.of(businesses.getFirst(), copy))).hasSize(1);
    }

    @Test
    void detailsRejectWhatIsNotAPhoneNumberOrAWebAddress() {
        PlaceDetails details = new PlaceDetails(" ", "x".repeat(300), "not a number", "ftp://files.example");
        assertThat(details).isEqualTo(PlaceDetails.NONE);
        assertThat(new PlaceDetails("Kafe", null, "0216 555 11 22", "HTTP://kafe.example/menu").website())
                .isEqualTo("HTTP://kafe.example/menu");
        assertThat(OsmPlaces.categoryOf(Map.of("place", "town"))).contains(PlaceCategory.SETTLEMENT);
    }

    private static InputStream resource(String name) {
        return BusinessDataTest.class.getClassLoader().getResourceAsStream(name);
    }
}
