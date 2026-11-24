package com.citynavigator.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Someone who signs in to keep their places on every device. The aggregate holds the account and its saved places;
 * the places are always replaced together, so an account never has two homes or more places than allowed.
 */
@Entity
@Table(name = "user_account")
public class UserAccount {

    public static final int EMAIL_MAX_LENGTH = 254;
    public static final int NAME_MAX_LENGTH = 60;
    /** As many saved places as the application offers (besides home and workplace). */
    public static final int MAX_SAVED_PLACES = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = EMAIL_MAX_LENGTH)
    private String email;

    @Column(name = "display_name", length = NAME_MAX_LENGTH)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "account", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position")
    private List<SavedPlace> places = new ArrayList<>();

    @Version
    private long version;

    protected UserAccount() {
        // for JPA
    }

    private UserAccount(String email, String displayName, String passwordHash, Instant createdAt) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    /**
     * A new account. The e-mail address is kept in lower case, so "Ali@Example.com" and "ali@example.com" are one
     * account; a blank name means none.
     */
    public static UserAccount open(String email, String displayName, String passwordHash, Instant now) {
        String address = normalizedEmail(email);
        if (address.length() > EMAIL_MAX_LENGTH || address.indexOf('@') < 1 || address.endsWith("@")) {
            throw new IllegalArgumentException("not an e-mail address: " + email);
        }
        String name = displayName == null || displayName.isBlank() ? null : displayName.strip();
        if (name != null && name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("a name has at most " + NAME_MAX_LENGTH + " characters");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("an account needs a password hash");
        }
        return new UserAccount(address, name, passwordHash, now);
    }

    /** How an e-mail address is stored and looked up. */
    public static String normalizedEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Replaces home, workplace and saved places at once.
     *
     * @param home  the home, or {@code null}
     * @param work  the workplace, or {@code null}
     * @param saved the saved places in their order, at most {@link #MAX_SAVED_PLACES}
     */
    public void replacePlaces(Place home, Place work, List<Place> saved) {
        if (saved.size() > MAX_SAVED_PLACES) {
            throw new IllegalArgumentException("at most " + MAX_SAVED_PLACES + " saved places");
        }
        List<SavedPlace> next = new ArrayList<>();
        if (home != null) {
            next.add(home.toSaved(this, next.size(), SavedPlaceKind.HOME));
        }
        if (work != null) {
            next.add(work.toSaved(this, next.size(), SavedPlaceKind.WORK));
        }
        for (Place place : saved) {
            next.add(place.toSaved(this, next.size(), SavedPlaceKind.PLACE));
        }
        places.clear();
        places.addAll(next);
    }

    public Optional<SavedPlace> home() {
        return places.stream().filter(place -> place.getKind() == SavedPlaceKind.HOME).findFirst();
    }

    public Optional<SavedPlace> work() {
        return places.stream().filter(place -> place.getKind() == SavedPlaceKind.WORK).findFirst();
    }

    public List<SavedPlace> saved() {
        return places.stream().filter(place -> place.getKind() == SavedPlaceKind.PLACE).toList();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * A place as the owner gives it, before it is saved.
     */
    public record Place(String label, String detail, double lat, double lon) {

        SavedPlace toSaved(UserAccount account, int position, SavedPlaceKind kind) {
            return new SavedPlace(account, position, kind, label, detail, lat, lon);
        }
    }
}
