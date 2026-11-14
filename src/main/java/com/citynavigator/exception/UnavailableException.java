package com.citynavigator.exception;

/**
 * A service the request depends on (the nationwide routing or search service) is not answering right now.
 */
public final class UnavailableException extends DomainException {

    private static final long serialVersionUID = 1L;

    public UnavailableException(String message) {
        super(message);
    }
}
