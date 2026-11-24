package com.citynavigator.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class UserAccountTest {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");

    @Test
    void anAddressIsOneAccountWhateverItsCase() {
        UserAccount account = UserAccount.open("  Ali.Veli@Example.COM ", "  Ali ", "hash", NOW);

        assertThat(account.getEmail()).isEqualTo("ali.veli@example.com");
        assertThat(account.getDisplayName()).isEqualTo("Ali");
        assertThat(UserAccount.open("a@b.c", " ", "hash", NOW).getDisplayName()).isNull();
        assertThatThrownBy(() -> UserAccount.open("ali", null, "hash", NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.open("ali@", null, "hash", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.open("a@b.c", "x".repeat(61), "hash", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> UserAccount.open("a@b.c", null, " ", NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void placesAreReplacedTogetherInTheirOrder() {
        UserAccount account = UserAccount.open("a@b.c", null, "hash", NOW);
        UserAccount.Place home = new UserAccount.Place(" Ev ", "Caferağa", 40.98, 29.02);
        UserAccount.Place pier = new UserAccount.Place("İskele", null, 40.99, 29.02);
        UserAccount.Place park = new UserAccount.Place("Park", " ", 40.97, 29.03);

        account.replacePlaces(home, null, List.of(pier, park));

        assertThat(account.home()).hasValueSatisfying(place -> {
            assertThat(place.getLabel()).isEqualTo("Ev");
            assertThat(place.getKind()).isEqualTo(SavedPlaceKind.HOME);
            assertThat(place.getPosition()).isZero();
        });
        assertThat(account.work()).isEmpty();
        assertThat(account.saved()).extracting(SavedPlace::getLabel).containsExactly("İskele", "Park");
        assertThat(account.saved()).extracting(SavedPlace::getPosition).containsExactly(1, 2);
        assertThat(account.saved().get(1).getDetail()).isNull();

        account.replacePlaces(null, pier, List.of());
        assertThat(account.home()).isEmpty();
        assertThat(account.work()).hasValueSatisfying(place -> assertThat(place.getLat()).isEqualTo(40.99));
        assertThat(account.saved()).isEmpty();
    }

    @Test
    void refusesMorePlacesThanAllowedOrPlacesThatAreNowhere() {
        UserAccount account = UserAccount.open("a@b.c", null, "hash", NOW);
        UserAccount.Place place = new UserAccount.Place("Yer", null, 41, 29);

        assertThatThrownBy(() -> account.replacePlaces(null, null,
                Collections.nCopies(UserAccount.MAX_SAVED_PLACES + 1, place)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.replacePlaces(new UserAccount.Place("Ev", null, 95, 29), null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> account.replacePlaces(new UserAccount.Place(" ", null, 41, 29), null, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
