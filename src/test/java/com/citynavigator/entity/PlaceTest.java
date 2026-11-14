package com.citynavigator.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.citynavigator.exception.BusinessRuleException;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PlaceTest {

    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @Test
    void storesATrimmedNameAndItsSearchKey() {
        Place place = new Place("  Moda İskelesi ", PlaceCategory.TRANSIT, 40.98, 29.02, "node/1", NOW);
        assertThat(place.getName()).isEqualTo("Moda İskelesi");
        assertThat(place.getSearchKey()).isEqualTo("moda iskelesi");
        assertThat(place.isFromOpenStreetMap()).isTrue();
        assertThat(place.getCreatedAt()).isEqualTo(NOW);
        assertThat(new Place("Ev", PlaceCategory.OTHER, 40.98, 29.02, null, NOW).isFromOpenStreetMap()).isFalse();
    }

    @Test
    void rejectsNamesThatCannotBeSearched() {
        assertThatThrownBy(() -> new Place(" ", PlaceCategory.OTHER, 40.98, 29.02, null, NOW))
                .isInstanceOf(BusinessRuleException.class).hasMessage("Ad boş olamaz");
        assertThatThrownBy(() -> new Place("!!!", PlaceCategory.OTHER, 40.98, 29.02, null, NOW))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("harf ya da rakam");
        assertThatThrownBy(() -> new Place("a".repeat(201), PlaceCategory.OTHER, 40.98, 29.02, null, NOW))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("200");
    }

    @Test
    void rejectsImpossibleCoordinates() {
        assertThatThrownBy(() -> new Place("Yer", PlaceCategory.OTHER, 95, 29.02, null, NOW))
                .isInstanceOf(BusinessRuleException.class).hasMessageContaining("koordinat");
    }

    @Test
    void categoriesHaveTurkishLabels() {
        assertThat(PlaceCategory.TRANSIT.label()).isEqualTo("Ulaşım");
        assertThat(PlaceCategory.NEIGHBOURHOOD.compareTo(PlaceCategory.HEALTH)).isNegative();
    }
}
