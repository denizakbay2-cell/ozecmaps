package com.citynavigator.repository;

import com.citynavigator.entity.UserAccount;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;

@ApplicationScoped
public class UserAccountRepository {

    @PersistenceContext(unitName = "navigatorPU")
    private EntityManager entityManager;

    /** The account of an e-mail address already in {@link UserAccount#normalizedEmail} form. */
    public Optional<UserAccount> findByEmail(String email) {
        return entityManager.createQuery("SELECT a FROM UserAccount a WHERE a.email = :email", UserAccount.class)
                .setParameter("email", email)
                .getResultStream()
                .findFirst();
    }

    public Optional<UserAccount> findById(long id) {
        return Optional.ofNullable(entityManager.find(UserAccount.class, id));
    }

    public UserAccount add(UserAccount account) {
        entityManager.persist(account);
        entityManager.flush();
        return account;
    }

    /** Writes the account's changes now, so that a conflict shows inside the service's transaction. */
    public void flush() {
        entityManager.flush();
    }

    /** Removes the account with its saved places. */
    public void remove(UserAccount account) {
        entityManager.remove(account);
    }
}
