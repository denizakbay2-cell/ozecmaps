package com.citynavigator.service;

import com.citynavigator.dto.AccountView;
import com.citynavigator.dto.LoginRequest;
import com.citynavigator.dto.RegisterRequest;
import com.citynavigator.dto.SavedPlaceView;
import com.citynavigator.dto.SavedPlaces;
import com.citynavigator.dto.SessionView;
import com.citynavigator.entity.SavedPlace;
import com.citynavigator.entity.UserAccount;
import com.citynavigator.exception.ConflictException;
import com.citynavigator.exception.UnauthorizedException;
import com.citynavigator.repository.UserAccountRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.time.Clock;
import java.util.List;
import java.util.Optional;

/**
 * Accounts: opening one, signing in, and the places each keeps (home, workplace, saved places), so that they are
 * the same on every device. A request is signed in by the token {@link #accountOf} checks.
 */
@ApplicationScoped
@Transactional
public class AccountService {

    static final String BEARER = "Bearer ";

    private UserAccountRepository accounts;
    private Passwords passwords;
    private SessionTokens tokens;
    private LoginAttempts attempts;
    private Clock clock;

    protected AccountService() {
        // for the CDI proxy
    }

    @Inject
    public AccountService(UserAccountRepository accounts, Passwords passwords, SessionTokens tokens,
                          LoginAttempts attempts, Clock clock) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.tokens = tokens;
        this.attempts = attempts;
        this.clock = clock;
    }

    public SessionView register(RegisterRequest request) {
        String email = UserAccount.normalizedEmail(request.email());
        if (accounts.findByEmail(email).isPresent()) {
            throw alreadyTaken();
        }
        UserAccount account;
        try {
            account = accounts.add(UserAccount.open(email, request.name(), passwords.hash(request.password()),
                    clock.instant()));
        } catch (PersistenceException e) {
            // someone opened the same address at the same moment
            throw alreadyTaken();
        }
        return session(account);
    }

    /**
     * Signs in. A wrong address and a wrong password get the same answer, in the same time, so the answer does not
     * tell which addresses have an account.
     */
    public SessionView login(LoginRequest request) {
        String email = UserAccount.normalizedEmail(request.email());
        attempts.check(email);
        Optional<UserAccount> account = accounts.findByEmail(email);
        if (account.isEmpty()) {
            passwords.checkAgainstNothing(request.password());
        }
        if (account.isEmpty() || !passwords.matches(request.password(), account.get().getPasswordHash())) {
            attempts.failed(email);
            throw new UnauthorizedException("E-posta adresi ya da şifre yanlış");
        }
        attempts.succeeded(email);
        return session(account.get());
    }

    /** The account the request's {@code Authorization: Bearer <token>} header signs in. */
    @Transactional(Transactional.TxType.SUPPORTS)
    public long accountOf(String authorization) {
        return Optional.ofNullable(authorization)
                .filter(header -> header.regionMatches(true, 0, BEARER, 0, BEARER.length()))
                .flatMap(header -> tokens.accountOf(header.substring(BEARER.length()).strip()))
                .orElseThrow(() -> new UnauthorizedException("Bunun için giriş yapmanız gerekiyor"));
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public AccountView account(long accountId) {
        return view(find(accountId));
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public SavedPlaces places(long accountId) {
        return places(find(accountId));
    }

    public SavedPlaces replacePlaces(long accountId, SavedPlaces request) {
        UserAccount account = find(accountId);
        account.replacePlaces(place(request.home()), place(request.work()),
                request.places().stream().map(AccountService::place).toList());
        accounts.flush();
        return places(account);
    }

    /** Removes the account and every place it kept. */
    public void delete(long accountId) {
        accounts.remove(find(accountId));
    }

    private UserAccount find(long accountId) {
        // a valid token of an account deleted since
        return accounts.findById(accountId)
                .orElseThrow(() -> new UnauthorizedException("Oturumunuz sona erdi; yeniden giriş yapın"));
    }

    private SessionView session(UserAccount account) {
        SessionTokens.Session session = tokens.issue(account.getId());
        return new SessionView(session.token(), session.expiresAt(), view(account));
    }

    private static AccountView view(UserAccount account) {
        return new AccountView(account.getEmail(), account.getDisplayName(), account.getCreatedAt());
    }

    private static SavedPlaces places(UserAccount account) {
        return new SavedPlaces(account.home().map(AccountService::view).orElse(null),
                account.work().map(AccountService::view).orElse(null),
                account.saved().stream().map(AccountService::view).toList());
    }

    private static SavedPlaceView view(SavedPlace place) {
        return new SavedPlaceView(place.getLabel(), place.getDetail(), place.getLat(), place.getLon());
    }

    private static UserAccount.Place place(SavedPlaceView view) {
        return view == null ? null : new UserAccount.Place(view.label(), view.detail(), view.lat(), view.lon());
    }

    private static ConflictException alreadyTaken() {
        return new ConflictException("Bu e-posta adresiyle açılmış bir hesap zaten var");
    }

    /** For tests: the places of no account. */
    static SavedPlaces none() {
        return new SavedPlaces(null, null, List.of());
    }
}
