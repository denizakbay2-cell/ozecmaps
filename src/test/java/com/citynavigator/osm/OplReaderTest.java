package com.citynavigator.osm;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OplReaderTest {

    @Test
    void readsNodesAndWaysWithTheirTagsAndNodeReferences() throws IOException {
        String opl = """
                n14455442 v3 dV c1 t2020-01-01T00:00:00Z i1 utest T x27.1413637 y38.4282601
                n14455443 v1 dV T
                n14455444 T x27.1423 y38.4290

                w3038929 v2 dV Thighway=residential,name=Plevne%20%Bulvar%131%,oneway=yes Nn14455442,n14455444
                r55 Ttype=route Mw3038929@
                w3038930 Thighway=service
                """;
        List<String> seen = new ArrayList<>();

        OplReader.read(new BufferedReader(new StringReader(opl)), new OplReader.Handler() {
            @Override
            public void node(long id, double lat, double lon) {
                seen.add("node " + id + " " + lat + " " + lon);
            }

            @Override
            public void way(long id, Map<String, String> tags, long[] nodes) {
                seen.add("way " + id + " " + new java.util.TreeMap<>(tags) + " " + java.util.Arrays.toString(nodes));
            }
        });

        assertThat(seen).containsExactly(
                "node 14455442 38.4282601 27.1413637",
                "node 14455444 38.429 27.1423",
                "way 3038929 {highway=residential, name=Plevne Bulvarı, oneway=yes} [14455442, 14455444]",
                "way 3038930 {highway=service} []");
    }

    @Test
    void unescapesHexCodesAndLeavesPlainTextAlone() {
        assertThat(OplReader.unescape("Kad%131%k%f6%y")).isEqualTo("Kadıköy");
        assertThat(OplReader.unescape("a%2c%b%3d%c")).isEqualTo("a,b=c");
        assertThat(OplReader.unescape("100%")).isEqualTo("100%");
        assertThat(OplReader.unescape("plain")).isEqualTo("plain");
    }
}
