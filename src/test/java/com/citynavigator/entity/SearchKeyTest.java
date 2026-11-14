package com.citynavigator.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchKeyTest {

    @Test
    void foldsTurkishLettersAndCase() {
        assertThat(SearchKey.of("KADIKÖY")).isEqualTo("kadikoy");
        assertThat(SearchKey.of("Kadıköy")).isEqualTo("kadikoy");
        assertThat(SearchKey.of("İstanbul")).isEqualTo("istanbul");
        assertThat(SearchKey.of("Çağlayan Şükrü Göğüş")).isEqualTo("caglayan sukru gogus");
        assertThat(SearchKey.of("Kâğıthane")).isEqualTo("kagithane");
    }

    @Test
    void keepsOnlyWordsSeparatedBySingleSpaces() {
        assertThat(SearchKey.of("  Dr. Esat Işık Cd. (No: 5) ")).isEqualTo("dr esat isik cd no 5");
        assertThat(SearchKey.of("19 Mayıs")).isEqualTo("19 mayis");
        assertThat(SearchKey.of("—")).isEmpty();
    }
}
