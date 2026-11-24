package com.citynavigator.exception;

/**
 * Too many failed attempts in a short time, for example wrong passwords for one account: try again later.
 */
public final class TooManyAttemptsException extends DomainException {

    private static final long serialVersionUID = 1L;

    public TooManyAttemptsException(String message) {
        super(message);
    }
}
