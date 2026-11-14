package com.citynavigator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.lenient;

import com.citynavigator.dto.AlongPlaceView;
import com.citynavigator.exception.BusinessRuleException;
import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.RoadsidePlace;
import com.citynavigator.routing.RoadsidePlace.Kind;
import com.citynavigator.routing.RoadsidePlaces;
import com.citynavigator.routing.TravelMode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlongRouteServiceTest {

    /** Along Ana Cadde, the main road of the test town, and on east beyond the town for about 1.7 km in all. */
    private static final List<GeoPoint> LINE = List.of(new GeoPoint(41.0, 29.0), new GeoPoint(41.0, 29.02));

    @Mock
    private RoutingEngine engine;
    private AlongRouteService service;

    @BeforeEach
    void setUp() {
        lenient().when(engine.snapshot()).thenReturn(ServiceFixture.snapshot());
        service = new AlongRouteService(engine, new RoadsidePlaces(List.of(
                new RoadsidePlace(Kind.PHARMACY, 41.00027, 29.0018, "Ana Eczane"),
                new RoadsidePlace(Kind.PHARMACY, 40.99946, 29.0006, "Güney Eczanesi"),
                new RoadsidePlace(Kind.PHARMACY, 41.0036, 29.0012, "Tepe Eczanesi"),
                new RoadsidePlace(Kind.PHARMACY, 41.0002, 29.015, "Dış Eczane"),
                new RoadsidePlace(Kind.FUEL, 41.0, 29.001, "Opet"))));
    }

    /** Google's encoded polyline with five decimals, as the application sends it. */
    static String encode(List<GeoPoint> points) {
        StringBuilder encoded = new StringBuilder();
        long lastLat = 0;
        long lastLon = 0;
        for (GeoPoint point : points) {
            long lat = Math.round(point.lat() * 1e5);
            long lon = Math.round(point.lon() * 1e5);
            append(lat - lastLat, encoded);
            append(lon - lastLon, encoded);
            lastLat = lat;
            lastLon = lon;
        }
        return encoded.toString();
    }

    private static void append(long value, StringBuilder encoded) {
        long rest = value < 0 ? ~(value << 1) : value << 1;
        while (rest >= 0x20) {
            encoded.append((char) ((0x20 | rest & 0x1f) + 63));
            rest >>= 5;
        }
        encoded.append((char) (rest + 63));
    }

    @Test
    void findsThePlacesOfAKindAlongTheRouteWithTheStreetTheyAreOn() {
        List<AlongPlaceView> found = service.along(Kind.PHARMACY, TravelMode.CAR, encode(LINE));

        assertThat(found).extracting(AlongPlaceView::name)
                .containsExactly("Güney Eczanesi", "Tepe Eczanesi", "Ana Eczane", "Dış Eczane");
        AlongPlaceView south = found.getFirst();
        assertThat(south.kind()).isEqualTo("Eczane");
        assertThat(south.detail()).isEqualTo("Ana Cadde");
        assertThat(south.side()).isEqualTo("RIGHT");
        assertThat(south.offsetMeters()).isEqualTo(60);
        assertThat((double) south.alongMeters()).isCloseTo(50, within(2.0));
        assertThat(found.get(2).side()).isEqualTo("LEFT");
        // no named road near it, and beyond the detailed area no street is looked for
        assertThat(found.get(1).detail()).isNull();
        assertThat(found.get(3).detail()).isNull();
    }

    @Test
    void walkersGetOnlyThePlacesCloseToTheirWay() {
        assertThat(service.along(Kind.PHARMACY, TravelMode.FOOT, encode(LINE))).extracting(AlongPlaceView::name)
                .containsExactly("Güney Eczanesi", "Ana Eczane", "Dış Eczane");
        assertThat(service.along(Kind.FUEL, TravelMode.FOOT, encode(LINE))).extracting(AlongPlaceView::kind)
                .containsExactly("Akaryakıt istasyonu");
    }

    @Test
    void rejectsALineThatIsNoRoute() {
        assertThatThrownBy(() -> service.along(Kind.PHARMACY, TravelMode.CAR, "_p~iF~ps|U_"))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Rota çizgisi okunamadı");
        // decodes to a latitude beyond the pole
        assertThatThrownBy(() -> service.along(Kind.PHARMACY, TravelMode.CAR, "~~~~~~~~~~_@??"))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Rota çizgisi okunamadı");
        assertThatThrownBy(() -> service.along(Kind.PHARMACY, TravelMode.CAR, encode(LINE.subList(0, 1))))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("en az iki nokta");
        List<GeoPoint> tooLong = new ArrayList<>();
        for (int i = 0; i <= AlongRouteService.MAX_POINTS; i++) {
            tooLong.add(new GeoPoint(41.0, 29.0 + i * 0.0001));
        }
        assertThatThrownBy(() -> service.along(Kind.PHARMACY, TravelMode.CAR, encode(tooLong)))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("en fazla");
    }

    @Test
    void theBundledListKnowsThePharmaciesOfKadikoy() {
        RoadsidePlaces bundled = AlongRouteService.load();
        // Söğütlüçeşme to the Bahariye side of Kadıköy's centre
        List<GeoPoint> centre = List.of(new GeoPoint(40.9925, 29.0370), new GeoPoint(40.9880, 29.0290));

        assertThat(bundled.size()).isGreaterThan(50_000);
        assertThat(bundled.along(centre, Kind.PHARMACY, 200)).isNotEmpty();
        assertThat(new AlongRouteService(engine, bundled).along(Kind.MONEY, TravelMode.FOOT, encode(centre)))
                .isNotEmpty().allSatisfy(place -> assertThat(place.offsetMeters()).isLessThanOrEqualTo(200));
    }
}
