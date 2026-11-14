package com.citynavigator.repository;

import com.citynavigator.entity.Place;
import com.citynavigator.entity.PlaceCategory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

@ApplicationScoped
public class PlaceRepository {

    private static final int FLUSH_EVERY = 500;

    @PersistenceContext(unitName = "navigatorPU")
    private EntityManager entityManager;

    /**
     * Places whose name has a word starting with each of the given words (already in {@code SearchKey} form).
     */
    public List<Place> findMatching(List<String> words, int maxResults) {
        if (words.isEmpty()) {
            return List.of();
        }
        StringJoiner conditions = new StringJoiner(" AND ");
        for (int i = 0; i < words.size(); i++) {
            conditions.add("CONCAT(' ', p.searchKey) LIKE :word" + i);
        }
        TypedQuery<Place> query = entityManager.createQuery("SELECT p FROM Place p WHERE " + conditions, Place.class);
        for (int i = 0; i < words.size(); i++) {
            query.setParameter("word" + i, "% " + words.get(i) + "%");
        }
        return query.setMaxResults(maxResults).getResultList();
    }

    /**
     * Whether a place with the same search key lies inside the box around the point.
     */
    public boolean existsNear(String searchKey, double lat, double lon, double latDegrees, double lonDegrees) {
        return entityManager.createQuery("SELECT COUNT(p) FROM Place p WHERE p.searchKey = :key"
                        + " AND p.lat BETWEEN :minLat AND :maxLat AND p.lon BETWEEN :minLon AND :maxLon", Long.class)
                .setParameter("key", searchKey)
                .setParameter("minLat", lat - latDegrees)
                .setParameter("maxLat", lat + latDegrees)
                .setParameter("minLon", lon - lonDegrees)
                .setParameter("maxLon", lon + lonDegrees)
                .getSingleResult() > 0;
    }

    /**
     * Businesses inside the rectangle, at most {@code max} of them.
     */
    public List<Place> findBusinessesWithin(double minLat, double minLon, double maxLat, double maxLon, int max) {
        return entityManager.createQuery("SELECT p FROM Place p WHERE p.business = TRUE"
                        + " AND p.lat BETWEEN :minLat AND :maxLat AND p.lon BETWEEN :minLon AND :maxLon"
                        + " ORDER BY p.id", Place.class)
                .setParameter("minLat", minLat)
                .setParameter("maxLat", maxLat)
                .setParameter("minLon", minLon)
                .setParameter("maxLon", maxLon)
                .setMaxResults(max)
                .getResultList();
    }

    /**
     * All places of the given categories, for example the neighbourhoods that name where other places lie.
     */
    public List<Place> findInCategories(Collection<PlaceCategory> categories) {
        return entityManager.createQuery("SELECT p FROM Place p WHERE p.category IN :categories", Place.class)
                .setParameter("categories", categories)
                .getResultList();
    }

    public Optional<Place> findById(long id) {
        return Optional.ofNullable(entityManager.find(Place.class, id));
    }

    public Place add(Place place) {
        entityManager.persist(place);
        entityManager.flush();
        return place;
    }

    /**
     * Stores an imported batch, flushing regularly so the persistence context stays small.
     */
    public void addAll(List<Place> places) {
        for (int i = 0; i < places.size(); i++) {
            entityManager.persist(places.get(i));
            if ((i + 1) % FLUSH_EVERY == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
    }

    public void remove(Place place) {
        entityManager.remove(place);
    }

    public long count() {
        return entityManager.createQuery("SELECT COUNT(p) FROM Place p", Long.class).getSingleResult();
    }
}
