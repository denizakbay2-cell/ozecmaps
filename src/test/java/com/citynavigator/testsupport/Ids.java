package com.citynavigator.testsupport;

import java.lang.reflect.Field;

/**
 * Gives an entity the id the database would have generated.
 */
public final class Ids {

    private Ids() {
    }

    public static <T> T assign(T entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
