package com.citynavigator.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.citynavigator.routing.GeoPoint;
import org.junit.jupiter.api.Test;

class PolylineTest {

    @Test
    void decodesGooglesReferenceExample() {
        assertThat(Polyline.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@", 5)).containsExactly(
                new GeoPoint(38.5, -120.2), new GeoPoint(40.7, -120.95), new GeoPoint(43.252, -126.453));
    }

    @Test
    void decodesSixDecimalsAndNothingFromNothing() {
        assertThat(Polyline.decode("_wockA_d_t}@", 6)).containsExactly(new GeoPoint(39.92, 32.85));
        assertThat(Polyline.decode("", 6)).isEmpty();
    }

    @Test
    void rejectsATruncatedPolyline() {
        assertThatThrownBy(() -> Polyline.decode("_p~iF~ps|U_", 5)).isInstanceOf(IllegalArgumentException.class);
    }
}
