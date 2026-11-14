package com.citynavigator.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.citynavigator.routing.GeoPoint;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhotonClientTest {

    @Test
    void readsNamesKindsPositionsAndAddresses() {
        List<GeocodedPlace> places = PhotonClient.parse(OsrmClientTest.json("external/photon-search.json"));

        assertThat(places).extracting(GeocodedPlace::name, GeocodedPlace::osmRef, GeocodedPlace::osmKey,
                GeocodedPlace::osmValue).startsWith(
                tuple("Migros", "node/2445263840", "shop", "supermarket"),
                tuple("Migros", "node/2201859837", "shop", "supermarket"));
        // two branches of the same chain: street, house number and neighbourhood tell them apart
        assertThat(places.getFirst().detail()).isEqualTo("Neşet Ömer Sokağı, Moda, Caferağa, Kadıköy, İstanbul");
        assertThat(places.get(1).detail()).isEqualTo("Mühürdar Caddesi No: 16 A, Bahariye, Caferağa, Kadıköy, İstanbul");
        assertThat(places.getFirst().lat()).isBetween(40.98, 41.0);
        assertThat(places.getFirst().lon()).isBetween(29.0, 29.1);
    }

    @Test
    void namesAnAddressWithoutANameAndSkipsWhatHasNeither() {
        JsonObject json = Json.createObjectBuilder().add("features", Json.createArrayBuilder()
                .add(feature(Json.createObjectBuilder().add("street", "Moda Caddesi").add("housenumber", "12")
                        .add("osm_type", "W").add("osm_id", 7).add("city", "Kadıköy")))
                .add(feature(Json.createObjectBuilder().add("osm_type", "N").add("osm_id", 8)))
                .add(feature(Json.createObjectBuilder().add("name", "Kadıköy").add("osm_type", "X").add("osm_id", 9)
                        .add("city", "Kadıköy").add("state", "İstanbul"))))
                .build();

        assertThat(PhotonClient.parse(json)).extracting(GeocodedPlace::name, GeocodedPlace::osmRef,
                GeocodedPlace::detail).containsExactly(
                tuple("Moda Caddesi No: 12", "way/7", "Kadıköy"),
                tuple("Kadıköy", null, "İstanbul"));
    }

    @Test
    void restrictsTheSearchToTurkeyAndRoundsTheBias() {
        PhotonClient client = new PhotonClient(new ExternalHttp(Duration.ofSeconds(1)), PhotonClient.KOMOOT,
                Clock.systemUTC());
        assertThat(client.uri(" kahve dünyası ", new GeoPoint(40.98765, 29.03456), 8)).hasToString(
                "https://photon.komoot.io/api/?q=kahve+d%C3%BCnyas%C4%B1&limit=8&lang=default"
                        + "&bbox=25.6,35.8,44.9,42.2&lat=40.99&lon=29.03");
        assertThat(client.uri("eczane", null, 5).getQuery()).doesNotContain("lat=");
    }

    private static JsonObject feature(jakarta.json.JsonObjectBuilder properties) {
        return Json.createObjectBuilder()
                .add("properties", properties)
                .add("geometry", Json.createObjectBuilder().add("coordinates", Json.createArrayBuilder().add(29.03).add(40.99)))
                .build();
    }
}
