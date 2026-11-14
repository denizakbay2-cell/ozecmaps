package com.citynavigator.exception;

/**
 * The request clashes with what is already stored, for example a place name that is taken at that spot.
 */
public final class ConflictException extends DomainException {

    private static final long serialVersionUID = 1L;

    public ConflictException(String message) {
        super(message);
    }
}
