package com.citynavigator.testsupport;

import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.TransitMode;
import com.citynavigator.routing.TransitNetwork;

/**
 * A small public transport network for the tests.
 * <pre>
 *        T (destination)
 *        D  metro and bus end, 2.2 km north of E
 *        |
 *   C    |  bus stop 60 m from A; bus 15 goes C to D, 08:05 to 09:00 (and Mondays 24:30 to 25:25)
 *   A ~~~~~~~~ B E   ferry A to B in 20 min, curving south; metro M9 from E (85 m from B) to D in 5 min
 *   O (start)
 * </pre>
 * The start and the destination are 3.5 km apart, too far to offer walking all the way.
 */
public final class TransitFixture {

    public static final GeoPoint START = new GeoPoint(40.9995, 29.0);
    public static final GeoPoint DESTINATION = new GeoPoint(41.0205, 29.031);
    public static final int A = 0;
    public static final int C = 1;
    public static final int B = 2;
    public static final int E = 3;
    public static final int D = 4;
    public static final byte MONDAY_TO_SATURDAY = 0b0111111;
    public static final byte SUNDAY = 0b1000000;
    public static final byte DAILY = 0b1111111;
    public static final byte MONDAY = 0b0000001;

    private TransitFixture() {
    }

    public static int at(int hours, int minutes) {
        return hours * 3600 + minutes * 60;
    }

    public static TransitNetwork network() {
        TransitNetwork.Builder builder = TransitNetwork.builder("test timetables");
        builder.addStop(41.0, 29.0, "Batı İskelesi");
        builder.addStop(41.0005, 29.0003, "İskele Durağı");
        builder.addStop(41.0, 29.03, "Doğu İskelesi");
        builder.addStop(41.0, 29.031, "Doğu");
        builder.addStop(41.02, 29.031, "Kuzey");
        int ferry = builder.addLine("", "Batı - Doğu", TransitMode.FERRY);
        int bus = builder.addLine("15", "İskele - Kuzey", TransitMode.BUS);
        int metro = builder.addLine("M9", "Doğu - Kuzey", TransitMode.METRO);
        // the ferry curves south between the piers
        builder.addPattern(ferry, "Doğu", new int[] {A, B},
                new int[] {at(8, 0), at(8, 20), at(8, 30), at(8, 50), at(9, 0), at(9, 20), at(10, 0), at(10, 20)},
                new byte[] {MONDAY_TO_SATURDAY, MONDAY_TO_SATURDAY, MONDAY_TO_SATURDAY, SUNDAY},
                new TransitNetwork.Shape(new double[] {41.0, 40.995, 41.0}, new double[] {29.0, 29.015, 29.03},
                        new int[] {0, 2}));
        builder.addPattern(bus, "Kuzey", new int[] {C, D},
                new int[] {at(8, 5), at(9, 0), at(24, 30), at(25, 25)}, new byte[] {DAILY, MONDAY});
        builder.addPattern(metro, "Kuzey", new int[] {E, D},
                new int[] {at(8, 25), at(8, 30), at(8, 55), at(9, 0), at(9, 25), at(9, 30)},
                new byte[] {DAILY, DAILY, DAILY});
        return builder.build();
    }
}
