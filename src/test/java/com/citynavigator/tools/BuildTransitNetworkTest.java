package com.citynavigator.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.citynavigator.routing.CongestionProfile;
import com.citynavigator.routing.TransitMode;
import com.citynavigator.routing.TransitNetwork;
import com.citynavigator.routing.TransitNetworkFile;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildTransitNetworkTest {

    @TempDir
    Path folder;

    @Test
    void repairsWhatTheFeedsBroke() {
        assertThat(BuildTransitNetwork.coordinate("410.191.700.005.564", 40.5, 41.8))
                .isCloseTo(41.0191700005564, within(1e-12));
        assertThat(BuildTransitNetwork.coordinate("41.120.759.000.573", 40.5, 41.8))
                .isCloseTo(41.120759000573, within(1e-12));
        assertThat(BuildTransitNetwork.coordinate("29.021731384099", 27.5, 30)).isEqualTo(29.021731384099);
        assertThat(BuildTransitNetwork.coordinate("41.00506785536913:17 17.03.20268", 40.5, 41.8)).isNaN();
        assertThat(BuildTransitNetwork.coordinate("4,12E+14", 27.5, 30)).isNaN();
        assertThat(BuildTransitNetwork.coordinate("39.92", 40.5, 41.8)).isNaN();

        assertThat(BuildTransitNetwork.seconds("06:40:00")).isEqualTo(24_000);
        assertThat(BuildTransitNetwork.seconds("25:10:00")).isEqualTo(90_600);
        assertThat(BuildTransitNetwork.seconds("")).isEqualTo(-1);
        assertThat(BuildTransitNetwork.seconds("yarın")).isEqualTo(-1);

        assertThat(BuildTransitNetwork.repaired("KADIKÃ–Y - KÄ°RAZLITEPE")).isEqualTo("KADIKÖY - KİRAZLITEPE");
        assertThat(BuildTransitNetwork.repaired("Kadıköy")).isEqualTo("Kadıköy");
        assertThat(BuildTransitNetwork.tidy("RIFAT  ILGAZ CADDESİ")).isEqualTo("Rıfat Ilgaz Caddesi");
        assertThat(BuildTransitNetwork.tidy("4.LEVENT METRO")).isEqualTo("4.Levent Metro");
        assertThat(BuildTransitNetwork.tidy("Kadıköy-Beşiktaş ŞH.")).isEqualTo("Kadıköy-Beşiktaş ŞH.");
    }

    @Test
    void namesLinesTheWayPassengersDo() {
        assertThat(BuildTransitNetwork.mode("1", "M4", "Metro İstanbul")).isEqualTo(TransitMode.METRO);
        assertThat(BuildTransitNetwork.mode("1", "Marmaray1", "TCDD")).isEqualTo(TransitMode.RAIL);
        assertThat(BuildTransitNetwork.mode("3", "34AS", "İETT")).isEqualTo(TransitMode.METROBUS);
        assertThat(BuildTransitNetwork.mode("3", "340", "İETT")).isEqualTo(TransitMode.BUS);
        assertThat(BuildTransitNetwork.mode("4", "KDK-BŞK", "Şehir Hatları")).isEqualTo(TransitMode.FERRY);
        assertThat(BuildTransitNetwork.mode("9", "MODA-KADIKÖY", "Minibus")).isEqualTo(TransitMode.MINIBUS);

        assertThat(BuildTransitNetwork.names(TransitMode.FERRY, "KDK-BŞK", "KADIKÖY - BEŞİKTAŞ"))
                .containsExactly("", "Kadıköy - Beşiktaş");
        assertThat(BuildTransitNetwork.names(TransitMode.METRO, "M4", "KADIKÖY - TAVŞANTEPE"))
                .containsExactly("M4", "Kadıköy - Tavşantepe");
        assertThat(BuildTransitNetwork.names(TransitMode.RAIL, "Marmaray1", "SÖĞÜTLÜÇEŞME - ZEYTİNBURNU"))
                .containsExactly("Marmaray", "Söğütlüçeşme - Zeytinburnu");
        assertThat(BuildTransitNetwork.names(TransitMode.MINIBUS, "BOSTANCI-KADIKÖY", "KADIKÖY ÇALIŞMA GRUBU"))
                .containsExactly("", "Bostancı-Kadıköy");
    }

    @Test
    void worksOutTheTimesBetweenTheStopsThatHaveOne() {
        double[] lat = {41.0, 41.0, 41.0, 41.0};
        double[] lon = {29.0, 29.001, 29.003, 29.004};
        int[] times = {0, -1, -1, 400};

        assertThat(BuildTransitNetwork.interpolate(lat, lon, times)).isTrue();
        assertThat(times).containsExactly(0, 100, 300, 400);
        assertThat(BuildTransitNetwork.interpolate(lat, lon, new int[] {0, -1, -1, -1})).isFalse();
        assertThat(BuildTransitNetwork.interpolate(lat, lon, new int[] {500, -1, -1, 400})).isFalse();
    }

    @Test
    void fitsAShapeToTheStopsAndKeepsOnlyThePointsItNeeds() {
        // east along 41° N in steps of about 84 m, then north; the stops are at the start, the bend and the end
        double[] lat = new double[21];
        double[] lon = new double[21];
        for (int point = 0; point <= 10; point++) {
            lat[point] = 41.0;
            lon[point] = 29.0 + point * 0.001;
        }
        for (int point = 11; point <= 20; point++) {
            lat[point] = 41.0 + (point - 10) * 0.001;
            lon[point] = 29.01;
        }
        double[] stopLat = {41.0001, 41.0, 41.0101};
        double[] stopLon = {29.0, 29.0102, 29.0101};

        int[] fitted = BuildTransitNetwork.fit(lat, lon, stopLat, stopLon);
        TransitNetwork.Shape shape = BuildTransitNetwork.simplified(lat, lon, fitted);

        assertThat(fitted).containsExactly(0, 10, 20);
        assertThat(shape.lat()).containsExactly(41.0, 41.0, 41.01);
        assertThat(shape.lon()).containsExactly(29.0, 29.01, 29.01);
        assertThat(shape.stopPoints()).containsExactly(0, 1, 2);
        // a stop far from the line: the shape is not this pattern's
        assertThat(BuildTransitNetwork.fit(lat, lon, new double[] {41.0, 41.05}, new double[] {29.0, 29.0})).isNull();
    }

    @Test
    void roadVehiclesTakeLongerWhenTrafficIsUsuallySlow() {
        // usually 80 % of the free-flow speed, 40 % at eight on weekdays; on Sundays always 80 %
        int[] percent = new int[72];
        java.util.Arrays.fill(percent, 80);
        percent[8] = 40;
        CongestionProfile traffic = CongestionProfile.builder()
                .add(com.citynavigator.testsupport.Geohash.encode(41.0, 29.005), percent).build();
        List<double[]> known = List.of(new double[] {41.0, 29.0045}, new double[] {41.0, 29.0055});
        List<double[]> unknown = List.of(new double[] {40.0, 29.0}, new double[] {40.0, 29.01});

        double[][] scales = BuildTransitNetwork.trafficScales(List.of(known, unknown), traffic);
        int[] times = {8 * 3600, 8 * 3600 + 600, 8 * 3600 + 1200};

        // the first ten minutes at eight take twice the usual share of free flow: about 19 minutes; nothing is known
        // about the second stretch
        int[] rushHour = BuildTransitNetwork.withTraffic(times, scales, 0);
        assertThat(rushHour[1] - rushHour[0]).isBetween(1_140, 1_170);
        assertThat(rushHour[2] - rushHour[1]).isEqualTo(600);
        // at noon traffic is a little faster than the day's average; times come in steps of 15 seconds
        int[] noon = BuildTransitNetwork.withTraffic(new int[] {12 * 3600, 12 * 3600 + 600, 12 * 3600 + 1200},
                scales, 0);
        assertThat(noon[1] - noon[0]).isEqualTo(585);
        assertThat(rushHour[1] % 15).isZero();
        // on Sundays the timetable stands
        assertThat(BuildTransitNetwork.withTraffic(times, scales, 2)).containsExactly(times);
        assertThat(BuildTransitNetwork.dayType((byte) 0b1111111)).isEqualTo(0);
        assertThat(BuildTransitNetwork.dayType((byte) 0b1100000)).isEqualTo(1);
        assertThat(BuildTransitNetwork.dayType((byte) 0b1000000)).isEqualTo(2);
    }

    @Test
    void readsTrafficEveryFewHundredMetresAlongTheWay() {
        // 2.5 km east along 41° N: eight points, the first 150 m in
        List<double[]> samples = BuildTransitNetwork.samples(List.of(new double[] {41.0, 29.0},
                new double[] {41.0, 29.01}, new double[] {41.0, 29.03}));
        assertThat(samples).hasSize(8);
        assertThat(samples.getFirst()[1]).isCloseTo(29.0 + 0.03 / 16, within(1e-6));
        assertThat(samples.getLast()[1]).isCloseTo(29.03 - 0.03 / 16, within(1e-6));
        assertThat(BuildTransitNetwork.samples(List.of(new double[] {41.0, 29.0}, new double[] {41.0, 29.001})))
                .singleElement().satisfies(middle -> assertThat(middle[1]).isCloseTo(29.0005, within(1e-9)));
    }

    @Test
    void splitsQuotedFieldsAndSeparators() throws IOException {
        assertThat(GtfsTable.split("1,\"Kadıköy, İskele\",\"a \"\"b\"\"\"", ','))
                .containsExactly("1", "Kadıköy, İskele", "a \"b\"");
        List<String> names = new ArrayList<>();
        GtfsTable.read("﻿stop_id;stop_name\n7;Moda\n\n8;Kadıköy\n", row -> names.add(row.get("stop_name")
                + row.get("missing")));
        assertThat(names).containsExactly("Moda", "Kadıköy");
    }

    /**
     * Two small feeds with the quirks of İBB's: one in Windows-1254 with commas, frequencies and a long-gone service;
     * one in UTF-8 with semicolons, broken coordinates, a twice-encoded line name and bus times only at the ends.
     */
    @Test
    void buildsTheTimetableFromBothKindsOfFeed() throws IOException {
        Path others = Files.createDirectories(folder.resolve("ibb"));
        Charset turkish = Charset.forName("windows-1254");
        write(others, "agency", "agency_id,agency_name\n6,Şehir Hatları\n37,Minibus\n", turkish);
        write(others, "calendar", """
                service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date
                26,1,1,1,1,1,0,0,20221231,20241231
                122,0,0,0,0,0,1,1,20190101,20190303
                """, turkish);
        write(others, "routes", """
                route_id,agency_id,route_short_name,route_long_name,route_type
                1,6,KDK-BŞK,"KADIKÖY - BEŞİKTAŞ",4
                2,37,MODA-KADIKÖY,KADIKÖY ÇALIŞMA GRUBU,9
                """, turkish);
        write(others, "stops", """
                stop_id,stop_name,stop_lat,stop_lon
                10,Kadıköy ŞH.,40.9917,29.0217
                11,Beşiktaş ŞH.,41.0417,29.0067
                12,Moda,40.9830,29.0260
                """, turkish);
        write(others, "trips", """
                route_id,service_id,trip_id,trip_headsign,shape_id
                1,26,100,Beşiktaş,S1
                1,122,101,Beşiktaş,S1
                2,26,200,Kadıköy,S2
                """, turkish);
        // the ferry swings out into the Bosphorus; the minibus's shape is somewhere else entirely
        write(others, "shapes", """
                shape_id,shape_pt_lat,shape_pt_lon,shape_pt_sequence
                S1,41.0417,29.0067,3
                S1,40.9917,29.0217,1
                S1,41.0150,29.0300,2
                S2,41.2,29.2,1
                S2,41.3,29.3,2
                """, turkish);
        write(others, "stop_times", """
                trip_id,arrival_time,departure_time,stop_id,stop_sequence
                100,08:25:00,08:25:15,11,2
                100,08:00:00,08:00:00,10,1
                101,09:00:00,09:00:00,10,1
                101,09:25:00,09:25:00,11,2
                200,00:00:00,00:00:00,12,1
                200,00:10:00,00:10:00,10,2
                """, turkish);
        write(others, "frequencies", "trip_id,start_time,end_time,headway_secs\n200,06:00:00,07:00:00,1200\n",
                turkish);

        Path buses = Files.createDirectories(folder.resolve("iett"));
        write(buses, "calendar", """
                ﻿service_id;service_name;monday;tuesday;wednesday;thursday;friday;saturday;sunday;start_date;end_date
                0;WEEKDAYS;1;1;1;1;1;0;0;20260316;20261231
                """, StandardCharsets.UTF_8);
        write(buses, "routes", """
                ﻿route_id;agency_id;route_short_name;route_long_name;route_type;route_desc;route_code
                1;1;14M;KADIKÃ–Y - ÃœMRANÄ°YE;3;;14M_D_D0
                2;1;34AS;AVCILAR - SÖĞÜTLÜÇEŞME;3;;34AS_D_D0
                """, StandardCharsets.UTF_8);
        write(buses, "stops", """
                ﻿stop_id;stop_code;stop_name;stop_desc;stop_lat;stop_lon;location_type;
                1;100;KADIKÖY;direction: ÜMRANİYE;409.917.000.000;290.230.000.000;0;
                2;101;ACIBADEM;;410.017.000.000;290.430.000.000;0;
                3;102;ÜMRANİYE;;410.217.000.000;291.030.000.000;0;
                4;103;BOZUK DURAK;;direction: GİDİŞ;4,12E+14;2,83E+14;
                """, StandardCharsets.UTF_8);
        write(buses, "trips", """
                ﻿trip_id;route_id;service_id;trip_headsign;direction_id
                500;1;0;ÜMRANİYE;0
                501;1;0;ÜMRANİYE;0
                """, StandardCharsets.UTF_8);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(buses.resolve("stop_times.zip")))) {
            zip.putNextEntry(new ZipEntry("stop_times.txt"));
            zip.write("""
                    trip_id,stop_id,stop_sequence,arrival_time,departure_time,timepoint
                    500,1,1,07:00:00,07:00:00,1
                    500,4,2,,,0
                    500,2,3,,,0
                    500,3,4,07:30:00,07:30:00,0
                    501,1,1,07:10:00,07:10:00,1
                    501,2,2,,,0
                    501,3,3,07:25:00,07:25:00,0
                    """.getBytes(StandardCharsets.UTF_8));
        }
        Path output = folder.resolve("transit.bin.gz");

        BuildTransitNetwork.build(output, "test source", CongestionProfile.NONE, List.of(others, buses));

        TransitNetwork network;
        try (InputStream in = Files.newInputStream(output)) {
            network = TransitNetworkFile.read(in);
        }
        assertThat(network.source()).isEqualTo("test source");
        // the broken stop is left out; stops join when a trip calls at them
        assertThat(IntStream.range(0, network.stopCount()).mapToObj(network::stopName))
                .containsExactly("Kadıköy ŞH.", "Beşiktaş ŞH.", "Moda", "Kadıköy", "Acıbadem", "Ümraniye");
        assertThat(network.stop(4).lat()).isCloseTo(41.0017, within(1e-9));
        assertThat(IntStream.range(0, network.lineCount()).mapToObj(line -> network.lineMode(line) + " "
                + network.lineName(line) + " " + network.lineLongName(line)))
                .containsExactly("FERRY  Kadıköy - Beşiktaş", "MINIBUS  Moda-Kadıköy", "BUS 14M Kadıköy - Ümraniye",
                        "METROBUS 34AS Avcılar - Söğütlüçeşme");
        // the ferry is drawn along its shape, the minibus from stop to stop
        assertThat(network.rideLine(0, 0, 1)).hasSize(3);
        assertThat(network.shape(1)).isNull();
        // the ferry runs on weekdays only: the trip of the service that ended in 2019 is gone
        assertThat(network.tripCount(0)).isEqualTo(1);
        assertThat(network.days(0, 0)).isEqualTo((byte) 0b0011111);
        assertThat(network.time(0, 0, 1)).isEqualTo(8 * 3600 + 25 * 60);
        // every twenty minutes from six to seven: three minibuses
        assertThat(network.tripCount(1)).isEqualTo(3);
        assertThat(network.time(1, 2, 1)).isEqualTo(6 * 3600 + 50 * 60);
        // the bus's middle stop gets a time in proportion to the distance
        assertThat(network.patternLength(2)).isEqualTo(3);
        int middle = network.time(2, 0, 1);
        assertThat(middle).isBetween(7 * 3600 + 60, 7 * 3600 + 29 * 60);
        assertThat(network.patternHeadsign(2)).isEqualTo("Ümraniye");
        // the second bus overtakes the first (07:10 to 07:25 against 07:00 to 07:30): a pattern of its own
        assertThat(network.patternCount()).isEqualTo(4);
        assertThat(network.tripCount(2) + network.tripCount(3)).isEqualTo(2);

        assertThatThrownBy(() -> BuildTransitNetwork.main(new String[] {output.toString()}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static void write(Path feed, String name, String text, Charset charset) throws IOException {
        try (OutputStream out = Files.newOutputStream(feed.resolve(name + ".txt"))) {
            out.write(text.getBytes(charset));
        }
    }
}
