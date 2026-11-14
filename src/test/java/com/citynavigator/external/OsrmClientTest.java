package com.citynavigator.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.Instruction;
import com.citynavigator.routing.Maneuver;
import com.citynavigator.routing.TravelMode;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class OsrmClientTest {

    @Test
    void turnsARealOsrmRouteIntoInstructions() {
        ExternalRoute route = OsrmClient.parse(json("external/osrm-route.json")).getFirst();

        assertThat(route.distanceMeters()).isCloseTo(1795, within(5.0));
        assertThat(route.instructions()).extracting(Instruction::maneuver, Instruction::street).containsExactly(
                tuple(Maneuver.DEPART, "Rıhtım Caddesi"),
                tuple(Maneuver.LEFT, null),
                tuple(Maneuver.CONTINUE, "Albay Faik Sözdener Caddesi"),
                tuple(Maneuver.CONTINUE, "Damga Sokağı"),
                tuple(Maneuver.SLIGHT_RIGHT, "Mühürdar Caddesi"),
                tuple(Maneuver.LEFT, "Mühürdar Karakolu Sokağı"),
                tuple(Maneuver.RIGHT, "Moda Caddesi"),
                tuple(Maneuver.LEFT, "Atıfet Sokağı"),
                tuple(Maneuver.RIGHT, "Moda Mektebi Sokağı"),
                tuple(Maneuver.LEFT, "Şair Nefi Sokağı"),
                tuple(Maneuver.ARRIVE, "Şair Nefi Sokağı"));
        assertThat(route.instructions().stream().mapToDouble(Instruction::distanceMeters).sum())
                .isCloseTo(route.distanceMeters(), within(2.0));
        assertThat(route.instructions().getFirst().pointIndex()).isZero();
        assertThat(route.instructions().getLast().pointIndex()).isEqualTo(route.points().size() - 1);
        assertThat(route.instructions()).extracting(Instruction::pointIndex).isSorted();
        assertThat(route.from().name()).isEqualTo("Rıhtım Caddesi");
        assertThat(route.from().distanceMeters()).isCloseTo(43.8, within(0.1));
        assertThat(route.points().getFirst()).isEqualTo(route.from().point());
    }

    @Test
    void foldsSilentStepsIntoTheOneBeforeAndKeepsRoundaboutExits() {
        ExternalRoute route = OsrmClient.parse(json("external/osrm-roundabout.json")).getFirst();

        assertThat(route.instructions()).extracting(Instruction::maneuver, Instruction::street,
                Instruction::exitNumber, Instruction::distanceMeters).containsExactly(
                tuple(Maneuver.DEPART, "D-200", 0, 200.0),
                tuple(Maneuver.ROUNDABOUT, "Kızılay Meydanı", 2, 350.0),
                tuple(Maneuver.SLIGHT_LEFT, "Atatürk Bulvarı", 0, 350.0),
                tuple(Maneuver.ARRIVE, "Atatürk Bulvarı", 0, 0.0));
        assertThat(route.instructions().getFirst().bearing()).isEqualTo(90);
        assertThat(route.from().name()).isNull();
        assertThat(route.to().distanceMeters()).isGreaterThan(1000);
        assertThat(route.points()).hasSize(5);
    }

    @Test
    void readsTheAlternativesAfterTheFastestRoute() {
        // Kızılay to Ulus in Ankara, asked with alternatives=1
        List<ExternalRoute> routes = OsrmClient.parse(json("external/osrm-alternatives.json"));

        assertThat(routes).hasSize(2);
        assertThat(routes).extracting(ExternalRoute::distanceMeters, ExternalRoute::durationSeconds)
                .containsExactly(tuple(4396.2, 369.9), tuple(6131.7, 395.0));
        assertThat(routes.get(1).instructions()).extracting(Instruction::street).contains("Dögol Caddesi");
        assertThat(routes.get(1).from()).isEqualTo(routes.getFirst().from());
        assertThat(routes.get(1).points().getFirst()).isEqualTo(routes.get(1).from().point());
        assertThat(routes.get(1).instructions().getLast().maneuver()).isEqualTo(Maneuver.ARRIVE);
    }

    @Test
    void noRouteIsEmptyAndOtherCodesAreFailures() {
        assertThat(OsrmClient.parse(Json.createObjectBuilder().add("code", "NoRoute").build())).isEmpty();
        assertThat(OsrmClient.parse(Json.createObjectBuilder().add("code", "NoSegment").build())).isEmpty();
        JsonObject tooBig = Json.createObjectBuilder().add("code", "TooBig").add("message", "too many").build();
        assertThatThrownBy(() -> OsrmClient.parse(tooBig))
                .isInstanceOf(ExternalServiceException.class).hasMessageContaining("TooBig");
    }

    @Test
    void mapsEveryModifier() {
        assertThat(OsrmClient.maneuverOf("turn", "uturn")).isEqualTo(Maneuver.U_TURN);
        assertThat(OsrmClient.maneuverOf("turn", "sharp right")).isEqualTo(Maneuver.SHARP_RIGHT);
        assertThat(OsrmClient.maneuverOf("turn", "sharp left")).isEqualTo(Maneuver.SHARP_LEFT);
        assertThat(OsrmClient.maneuverOf("merge", "slight right")).isEqualTo(Maneuver.SLIGHT_RIGHT);
        assertThat(OsrmClient.maneuverOf("continue", "straight")).isEqualTo(Maneuver.CONTINUE);
        assertThat(OsrmClient.maneuverOf("rotary", "")).isEqualTo(Maneuver.ROUNDABOUT);
        assertThat(OsrmClient.maneuverOf("notification", "")).isNull();
    }

    @Test
    void asksForTheProfileOfTheTravelModeInLongitudeLatitudeOrder() {
        OsrmClient client = new OsrmClient(new ExternalHttp(Duration.ofSeconds(1)), OsrmClient.FOSSGIS,
                Clock.systemUTC());
        assertThat(client.uri(new GeoPoint(40.9917, 29.0227), new GeoPoint(39.9334, 32.8597), TravelMode.BICYCLE, 0))
                .hasToString("https://routing.openstreetmap.de/routed-bike/route/v1/driving/29.022700,40.991700;"
                        + "32.859700,39.933400?overview=false&geometries=polyline6&steps=true");
        assertThat(client.uri(new GeoPoint(1, 2), new GeoPoint(3, 4), TravelMode.FOOT, 0).getPath())
                .contains("routed-foot");
        assertThat(client.uri(new GeoPoint(1, 2), new GeoPoint(3, 4), TravelMode.CAR, 1).getQuery())
                .endsWith("&alternatives=1");
    }

    static JsonObject json(String resource) {
        InputStream input = OsrmClientTest.class.getClassLoader().getResourceAsStream(resource);
        return Json.createReader(input).readObject();
    }
}
