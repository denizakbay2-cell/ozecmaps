package com.citynavigator.repository;

import com.citynavigator.entity.MapArea;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Optional;

@ApplicationScoped
public class MapAreaRepository {

    @PersistenceContext(unitName = "navigatorPU")
    private EntityManager entityManager;

    public Optional<MapArea> find() {
        return Optional.ofNullable(entityManager.find(MapArea.class, MapArea.SINGLE_ROW));
    }

    public void save(MapArea area) {
        entityManager.persist(area);
    }
}
