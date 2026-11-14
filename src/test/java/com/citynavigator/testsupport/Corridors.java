package com.citynavigator.testsupport;

import static com.citynavigator.testsupport.GraphFixture.ALL;

import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.RoadClass;

/**
 * Two corridors between a short street in the west and one in the east: the shape alternative routes are for.
 * <pre>
 *              10 -------------------- 11                Kuzey Yolu
 *             /                          \
 *   0 ----- 1                              2 ----- 3     Giriş and Çıkış: residential, 100 m each
 *             \                          /
 *              20 -------------------- 21                Güney Yolu
 * </pre>
 * Each corridor is about 883 m long. The start lies in the middle of Giriş, the destination in the middle of Çıkış,
 * so only these two 50 m stretches belong to every route.
 */
public final class Corridors {

    public static final GeoPoint START = GraphFixture.at(0, 0.5);
    public static final GeoPoint DESTINATION = GraphFixture.at(0, 9.5);

    private Corridors() {
    }

    public static GraphFixture of(RoadClass north, RoadClass south) {
        return new GraphFixture()
                .node(0, 0, 0).node(1, 0, 1).node(10, 1, 2).node(11, 1, 8).node(2, 0, 9).node(3, 0, 10)
                .node(20, -1, 2).node(21, -1, 8)
                .way(30, "Giriş", RoadClass.RESIDENTIAL, false, ALL, 0, 1)
                .way(31, "Kuzey Yolu", north, false, ALL, 1, 10, 11, 2)
                .way(32, "Güney Yolu", south, false, ALL, 1, 20, 21, 2)
                .way(33, "Çıkış", RoadClass.RESIDENTIAL, false, ALL, 2, 3);
    }
}
