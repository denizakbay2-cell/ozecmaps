package com.citynavigator.repository;

import com.citynavigator.entity.Incident;
import com.citynavigator.entity.IncidentStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class IncidentRepository {

    @PersistenceContext(unitName = "navigatorPU")
    private EntityManager entityManager;

    /**
     * Incidents that affect traffic at that moment, newest first. Their segments come along (eager collection).
     */
    public List<Incident> findActive(Instant now) {
        return entityManager.createQuery("SELECT i FROM Incident i WHERE i.status = :active"
                        + " AND (i.endsAt IS NULL OR i.endsAt > :now) ORDER BY i.reportedAt DESC", Incident.class)
                .setParameter("active", IncidentStatus.ACTIVE)
                .setParameter("now", now)
                .getResultList();
    }

    public Optional<Incident> findById(long id) {
        return Optional.ofNullable(entityManager.find(Incident.class, id));
    }

    public Incident add(Incident incident) {
        entityManager.persist(incident);
        entityManager.flush();
        return incident;
    }

    public boolean isEmpty() {
        return entityManager.createQuery("SELECT COUNT(i) FROM Incident i", Long.class).getSingleResult() == 0;
    }

    /**
     * Writes pending changes now, so a concurrent update fails inside the service call with an optimistic lock
     * error instead of at commit.
     */
    public void flush() {
        entityManager.flush();
    }
}
