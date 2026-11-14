package com.citynavigator.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.citynavigator.osm.OplReader;
import com.citynavigator.routing.CongestionProfile;
import com.citynavigator.routing.DayTime;
import com.citynavigator.routing.NationalGraph;
import com.citynavigator.routing.NationalGraphFile;
import com.citynavigator.routing.RoadsidePlace.Kind;
import com.citynavigator.routing.RoadsidePlaces;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildToolsTest {

    private static final String HEADER = "\"DATE_TIME\",\"LATITUDE\",\"LONGITUDE\",GEOHASH,MINIMUM_SPEED,"
            + "MAXIMUM_SPEED,AVERAGE_SPEED,NUMBER_OF_VEHICLES";

    @TempDir
    Path folder;

    /**
     * One cell with two weekdays of hourly data, half as fast at 08:00 and 18:00 as otherwise, and a Saturday evening;
     * another cell with too few hours to say anything.
     */
    private static String densityCsv() {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (int day = 2; day <= 3; day++) {
            for (int hour = 0; hour < 24; hour++) {
                int speed = hour == 8 || hour == 18 ? 30 : 60;
                csv.append(String.format("\"2024-10-0%d %02d:00:00\",\"41.064\",\"29.108\",sxk9w7,10,90,%d,40%n",
                        day, hour, speed));
            }
        }
        csv.append("\"2024-10-05 18:00:00\",\"41.064\",\"29.108\",sxk9w7,10,90,30,40\n");
        csv.append("\"2024-10-05 19:00:00\",\"41.064\",\"29.108\",sxk9w7,10,90,30,5\n");
        csv.append("\"2024-10-02 08:00:00\",\"40.99\",\"29.02\",sxk95z,10,90,40,100\n");
        return csv.toString();
    }

    @Test
    void keepsThePlacesATravellerStopsAtUnderTheirBestName() {
        assertThat(BuildPoiIndex.kind(Map.of("amenity", "fuel"))).contains(Kind.FUEL);
        assertThat(BuildPoiIndex.kind(Map.of("amenity", "charging_station"))).contains(Kind.CHARGING);
        assertThat(BuildPoiIndex.kind(Map.of("amenity", "atm"))).contains(Kind.MONEY);
        assertThat(BuildPoiIndex.kind(Map.of("shop", "convenience"))).contains(Kind.MARKET);
        assertThat(BuildPoiIndex.kind(Map.of("amenity", "cafe", "shop", "bakery"))).isEmpty();

        assertThat(BuildPoiIndex.name(Map.of("name", "Opet", "name:tr", "Opet Kadıköy"), Kind.FUEL))
                .isEqualTo("Opet Kadıköy");
        assertThat(BuildPoiIndex.name(Map.of("name", " ", "brand", "Shell;Turkey  Petrol"), Kind.FUEL))
                .isEqualTo("Shell,Turkey Petrol");
        assertThat(BuildPoiIndex.name(Map.of("amenity", "atm"), Kind.MONEY)).isEqualTo("ATM");
        assertThat(BuildPoiIndex.name(Map.of("amenity", "bank"), Kind.MONEY)).isEqualTo("Banka");
        assertThat(BuildPoiIndex.name(Map.of(), Kind.CHARGING)).isEqualTo("Şarj istasyonu");
        assertThat(BuildPoiIndex.name(Map.of("name", "x".repeat(100)), Kind.MARKET)).hasSize(80);
    }

    @Test
    void writesPointsAndTheMiddleOfOutlinesAsALineEachThatTheApplicationReads() throws IOException {
        Path opl = folder.resolve("places.opl");
        Files.writeString(opl, """
                n1 T x29.0 y41.0
                n2 T x29.002 y41.0
                n3 T x29.002 y41.002
                n4 T x29.0 y41.002
                n5 Tamenity=pharmacy,name=Moda%20%Eczanesi x29.03 y40.98
                n6 Thighway=bus_stop x29.04 y40.97
                w7 Tamenity=fuel,brand=Opet Nn1,n2,n3,n4,n1
                w8 Tbuilding=yes Nn1,n2,n3
                w9 Tshop=supermarket Nn404
                """);
        BuildPoiIndex.Collector collector = new BuildPoiIndex.Collector();
        try (BufferedReader reader = Files.newBufferedReader(opl)) {
            OplReader.read(reader, collector);
        }
        StringWriter text = new StringWriter();

        BuildPoiIndex.write(collector.rows(), "2026-11-13T21:22:20Z", new PrintWriter(text));

        assertThat(text.toString().lines()).containsExactly(
                "# places along routes: KIND;lat;lon;name",
                "# OpenStreetMap data of 2026-11-13T21:22:20Z, (c) OpenStreetMap contributors, ODbL",
                "FUEL;41.00100;29.00100;Opet",
                "PHARMACY;40.98000;29.03000;Moda Eczanesi");

        Path list = folder.resolve("places.csv.gz");
        BuildPoiIndex.main(new String[] {opl.toString(), list.toString()});
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(list)), StandardCharsets.UTF_8))) {
            assertThat(RoadsidePlaces.read(reader).size()).isEqualTo(2);
        }
        assertThatThrownBy(() -> BuildPoiIndex.main(new String[] {opl.toString()}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void condensesHourlyDensityIntoTheUsualShareOfFreeFlow() throws IOException {
        Map<String, BuildTrafficProfile.Cell> cells = new TreeMap<>();
        BuildTrafficProfile.read(new BufferedReader(new StringReader(densityCsv())), cells);
        StringWriter text = new StringWriter();

        int written = BuildTrafficProfile.write(cells, new PrintWriter(text));

        assertThat(cells).containsOnlyKeys("sxk9w7", "sxk95z");
        assertThat(written).isEqualTo(1);
        CongestionProfile profile = CongestionProfile.read(new java.io.ByteArrayInputStream(
                text.toString().getBytes(StandardCharsets.UTF_8)));
        assertThat(profile.cellCount()).isEqualTo(1);
        assertThat(profile.factor(41.064, 29.108, new DayTime(DayTime.WEEKDAY, 8))).isEqualTo(0.5);
        assertThat(profile.factor(41.064, 29.108, new DayTime(DayTime.WEEKDAY, 3))).isEqualTo(1.0);
        assertThat(profile.factor(41.064, 29.108, new DayTime(DayTime.SATURDAY, 18))).isEqualTo(0.5);
        // five vehicles are too few to tell anything
        assertThat(profile.factor(41.064, 29.108, new DayTime(DayTime.SATURDAY, 19))).isNaN();
        assertThat(text.toString()).contains("IBB Open Data License");
    }

    @Test
    void writesAGzippedProfileFromMonthlyFiles() throws IOException {
        Path month = folder.resolve("traffic_density_202410.csv");
        Files.writeString(month, densityCsv());
        Path output = folder.resolve("profile.csv.gz");

        BuildTrafficProfile.main(new String[] {output.toString(), month.toString()});

        try (InputStream in = new GZIPInputStream(Files.newInputStream(output))) {
            assertThat(CongestionProfile.read(in).cellCount()).isEqualTo(1);
        }
        assertThatThrownBy(() -> BuildTrafficProfile.main(new String[] {output.toString()}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildsTheNationalGraphFromCarRoadsInOplText() throws IOException {
        Path opl = folder.resolve("roads.opl");
        Files.writeString(opl, """
                n1 T x29.0000 y41.0000
                n2 T x29.0010 y41.0000
                n3 T x29.0020 y41.0000
                n4 T x29.0010 y41.0010
                n5 T x29.0030 y41.0000
                w10 Thighway=primary,name=Ana%20%Cadde,maxspeed=50 Nn1,n2,n3
                w11 Thighway=residential,oneway=yes Nn2,n4
                w12 Thighway=service,service=parking_aisle Nn3,n5
                w13 Thighway=footway Nn3,n5
                """);
        Path output = folder.resolve("roads.graph");

        BuildNationalGraph.main(new String[] {opl.toString(), output.toString(), "2026-11-13T21:22:20Z"});
        NationalGraph graph = NationalGraphFile.read(output);

        assertThat(graph.nodeCount()).isEqualTo(4);
        assertThat(graph.edgeCount()).isEqualTo(3);
        assertThat(graph.wayCount()).isEqualTo(2);
        assertThat(graph.dataTimestamp()).isEqualTo(Instant.parse("2026-11-13T21:22:20Z"));
        assertThat(graph.name(0)).isEqualTo("Ana Cadde");
        assertThat(graph.carSpeedKmh(0)).isEqualTo(50);
        assertThat(BuildNationalGraph.build(opl, null).dataTimestamp()).isNull();
        assertThatThrownBy(() -> BuildNationalGraph.main(new String[] {opl.toString()}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
