package com.citynavigator.exception;

/**
 * A place or incident with the requested id does not exist.
 */
public final class ResourceNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String what, long id) {
        super(what + " bulunamadı (no " + id + ")");
    }
}
