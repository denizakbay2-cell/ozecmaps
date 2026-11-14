package com.citynavigator.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class GeoMathTest {

    @Test
    void oneDegreeAlongTheEquatorIsAbout111Kilometres() {
        assertThat(GeoMath.distance(0, 0, 0, 1)).isCloseTo(111_195, within(1.0));
        assertThat(GeoMath.distance(0, 0, 1, 0)).isCloseTo(111_195, within(1.0));
    }

    @Test
    void kadikoyPierToModaIsAboutOneAndAHalfKilometres() {
        GeoPoint pier = new GeoPoint(40.9917, 29.0227);
        GeoPoint moda = new GeoPoint(40.9813, 29.0262);
        assertThat(GeoMath.distance(pier, moda)).isCloseTo(1_195, within(10.0));
        assertThat(GeoMath.distance(pier, pier)).isZero();
    }

    @Test
    void bearingsFollowTheCompass() {
        assertThat(GeoMath.bearing(0, 0, 1, 0)).isCloseTo(0, within(1e-9));
        assertThat(GeoMath.bearing(0, 0, 0, 1)).isCloseTo(90, within(1e-9));
        assertThat(GeoMath.bearing(0, 0, -1, 0)).isCloseTo(180, within(1e-9));
        assertThat(GeoMath.bearing(0, 0, 0, -1)).isCloseTo(270, within(1e-9));
    }

    @Test
    void turnAnglesArePositiveToTheRightAndWrapAround() {
        assertThat(GeoMath.turnAngle(0, 90)).isEqualTo(90);
        assertThat(GeoMath.turnAngle(0, 270)).isEqualTo(-90);
        assertThat(GeoMath.turnAngle(350, 10)).isEqualTo(20);
        assertThat(GeoMath.turnAngle(10, 350)).isEqualTo(-20);
        assertThat(GeoMath.turnAngle(90, 270)).isEqualTo(180);
        assertThat(GeoMath.turnAngle(270, 90)).isEqualTo(180);
    }

    @Test
    void projectionFindsTheClosestPointOfASegment() {
        // segment from (41.0, 29.0) to (41.0, 29.002), point 50 m north of its middle
        double north = 50 / GeoMath.METERS_PER_DEGREE;
        GeoMath.Projection middle = GeoMath.project(41.0 + north, 29.001, 41.0, 29.0, 41.0, 29.002);
        assertThat(middle.fraction()).isCloseTo(0.5, within(1e-6));
        assertThat(middle.distanceMeters()).isCloseTo(50, within(0.01));

        GeoMath.Projection beyondTheEnd = GeoMath.project(41.0, 29.003, 41.0, 29.0, 41.0, 29.002);
        assertThat(beyondTheEnd.fraction()).isEqualTo(1);

        GeoMath.Projection onAPoint = GeoMath.project(41.0, 29.0, 41.0, 29.0, 41.0, 29.0);
        assertThat(onAPoint.fraction()).isZero();
        assertThat(onAPoint.distanceMeters()).isZero();
    }

    @Test
    void interpolationMovesAlongTheSegment() {
        GeoPoint quarter = GeoMath.interpolate(41.0, 29.0, 41.004, 29.008, 0.25);
        assertThat(quarter.lat()).isCloseTo(41.001, within(1e-12));
        assertThat(quarter.lon()).isCloseTo(29.002, within(1e-12));
    }

    @Test
    void coordinatesOutsideTheGlobeAreRejected() {
        assertThatThrownBy(() -> new GeoPoint(91, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(0, -181)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GeoPoint(Double.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
