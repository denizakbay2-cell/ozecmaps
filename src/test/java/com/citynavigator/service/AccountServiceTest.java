package com.citynavigator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.citynavigator.dto.LoginRequest;
import com.citynavigator.dto.RegisterRequest;
import com.citynavigator.dto.SavedPlaceView;
import com.citynavigator.dto.SavedPlaces;
import com.citynavigator.dto.SessionView;
import com.citynavigator.entity.UserAccount;
import com.citynavigator.exception.ConflictException;
import com.citynavigator.exception.TooManyAttemptsException;
import com.citynavigator.exception.UnauthorizedException;
import com.citynavigator.repository.UserAccountRepository;
import jakarta.persistence.PersistenceException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final byte[] SECRET = "a secret of thirty-two characters!".getBytes(StandardCharsets.UTF_8);

    @Mock
    private UserAccountRepository repository;
    /** The "database": accounts by e-mail address, with ids handed out as they are added. */
    private final Map<String, UserAccount> stored = new HashMap<>();
    private final Passwords passwords = new Passwords(1_000);
    private final SessionTokens tokens = new SessionTokens(CLOCK, SECRET);
    private AccountService service;

    @BeforeEach
    void setUp() {
        lenient().when(repository.findByEmail(anyString()))
                .thenAnswer(call -> Optional.ofNullable(stored.get(call.<String>getArgument(0))));
        lenient().when(repository.findById(anyLong())).thenAnswer(call -> stored.values().stream()
                .filter(account -> account.getId() == call.<Long>getArgument(0)).findFirst());
        lenient().when(repository.add(any())).thenAnswer(call -> {
            UserAccount account = call.getArgument(0);
            Field id = UserAccount.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(account, (long) stored.size() + 1);
            stored.put(account.getEmail(), account);
            return account;
        });
        service = new AccountService(repository, passwords, tokens, new LoginAttempts(CLOCK), CLOCK);
    }

    @Test
    void anAccountKeepsAHashNotThePassword() {
        SessionView session = service.register(new RegisterRequest("Deniz@Example.com", "kadikoy-moda", "Deniz"));

        UserAccount account = stored.get("deniz@example.com");
        assertThat(account.getPasswordHash()).startsWith("pbkdf2-sha256$1000$").doesNotContain("kadikoy-moda");
        assertThat(session.account().email()).isEqualTo("deniz@example.com");
        assertThat(session.account().name()).isEqualTo("Deniz");
        assertThat(session.expiresAt()).isEqualTo(NOW.plus(SessionTokens.LIFETIME));
        assertThat(service.accountOf("Bearer " + session.token())).isEqualTo(account.getId());
        assertThatThrownBy(() -> service.register(new RegisterRequest("deniz@example.com", "baska-sifre", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void twoPeopleOpeningTheSameAddressAtOnceGetAConflict() {
        org.mockito.Mockito.doThrow(new PersistenceException("duplicate key")).when(repository).add(any());

        assertThatThrownBy(() -> service.register(new RegisterRequest("ayni@example.com", "12345678", null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void signingInNeedsTheRightPasswordAndSaysNothingAboutWhichPartWasWrong() {
        service.register(new RegisterRequest("deniz@example.com", "kadikoy-moda", null));

        SessionView session = service.login(new LoginRequest(" DENIZ@example.com", "kadikoy-moda"));
        assertThat(service.account(service.accountOf("bearer " + session.token())).email())
                .isEqualTo("deniz@example.com");
        assertThatThrownBy(() -> service.login(new LoginRequest("deniz@example.com", "yanlis-sifre")))
                .isInstanceOf(UnauthorizedException.class).hasMessage("E-posta adresi ya da şifre yanlış");
        assertThatThrownBy(() -> service.login(new LoginRequest("kimse@example.com", "kadikoy-moda")))
                .isInstanceOf(UnauthorizedException.class).hasMessage("E-posta adresi ya da şifre yanlış");
    }

    @Test
    void tooManyWrongPasswordsStopFurtherTriesForAWhile() {
        service.register(new RegisterRequest("deniz@example.com", "kadikoy-moda", null));
        for (int attempt = 0; attempt < LoginAttempts.MAX_FAILURES; attempt++) {
            assertThatThrownBy(() -> service.login(new LoginRequest("deniz@example.com", "tahmin")))
                    .isInstanceOf(UnauthorizedException.class);
        }

        assertThatThrownBy(() -> service.login(new LoginRequest("deniz@example.com", "kadikoy-moda")))
                .isInstanceOf(TooManyAttemptsException.class).hasMessageContaining("15 dakika");
        // a quarter of an hour later the right password works again
        AccountService later = new AccountService(repository, passwords, tokens,
                new LoginAttempts(Clock.offset(CLOCK, LoginAttempts.WINDOW.plusSeconds(1))), CLOCK);
        assertThat(later.login(new LoginRequest("deniz@example.com", "kadikoy-moda")).token()).isNotBlank();
    }

    @Test
    void requestsWithoutAGenuineTokenAreNotSignedIn() {
        SessionView session = service.register(new RegisterRequest("deniz@example.com", "kadikoy-moda", null));
        String token = session.token();

        assertThatThrownBy(() -> service.accountOf(null)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.accountOf(token)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.accountOf("Bearer " + token.replace("v1.1.", "v1.2.")))
                .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.accountOf("Bearer " + token + "x")).isInstanceOf(UnauthorizedException.class);
        // another server's secret, an expired token
        assertThat(new SessionTokens(CLOCK, "another secret of thirty-two chars".getBytes(StandardCharsets.UTF_8))
                .accountOf(token)).isEmpty();
        assertThat(new SessionTokens(Clock.offset(CLOCK, SessionTokens.LIFETIME.plusSeconds(1)), SECRET)
                .accountOf(token)).isEmpty();
        assertThat(tokens.accountOf("v1.x.y.z")).isEmpty();
        assertThat(tokens.accountOf("v2.1.2.3")).isEmpty();
    }

    @Test
    void anAccountKeepsItsPlacesForEveryDevice() {
        long id = service.accountOf("Bearer " + service.register(
                new RegisterRequest("deniz@example.com", "kadikoy-moda", null)).token());
        SavedPlaceView home = new SavedPlaceView("Ev", "Caferağa, Kadıköy", 40.984, 29.026);
        SavedPlaceView pier = new SavedPlaceView("Kadıköy İskelesi", null, 40.991, 29.022);

        assertThat(service.places(id)).isEqualTo(AccountService.none());
        SavedPlaces saved = service.replacePlaces(id, new SavedPlaces(home, null, List.of(pier)));

        assertThat(saved).isEqualTo(new SavedPlaces(home, null, List.of(pier)));
        assertThat(service.places(id)).isEqualTo(saved);
        verify(repository).flush();
    }

    @Test
    void aDeletedAccountSignsNothingInAnyMore() {
        String token = service.register(new RegisterRequest("deniz@example.com", "kadikoy-moda", null)).token();
        long id = service.accountOf("Bearer " + token);
        when(repository.findById(id)).thenReturn(Optional.of(stored.get("deniz@example.com")), Optional.empty());

        service.delete(id);

        verify(repository).remove(any());
        assertThatThrownBy(() -> service.places(id)).isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("yeniden giriş");
    }

    @Test
    void passwordsAreCheckedAgainstTheirOwnSaltAndIterations() {
        String first = passwords.hash("kadikoy-moda");
        String second = passwords.hash("kadikoy-moda");

        assertThat(first).isNotEqualTo(second);
        assertThat(passwords.matches("kadikoy-moda", first)).isTrue();
        assertThat(passwords.matches("kadikoy-moda", second)).isTrue();
        assertThat(passwords.matches("Kadikoy-moda", first)).isFalse();
        assertThat(new Passwords(2_000).matches("kadikoy-moda", first)).isTrue();
        assertThat(passwords.matches("kadikoy-moda", "md5$abc")).isFalse();
        assertThat(passwords.matches("kadikoy-moda", "pbkdf2-sha256$x$y$z")).isFalse();
        assertThat(passwords.matches("kadikoy-moda", "pbkdf2-sha256$1000$!!$??")).isFalse();
        assertThat(passwords.matches("kadikoy-moda", null)).isFalse();
        assertThat(new RegisterRequest("a@b.c", "gizli-sifre", null).toString()).doesNotContain("gizli");
        assertThat(new LoginRequest("a@b.c", "gizli-sifre").toString()).doesNotContain("gizli");
    }
}
