package com.citynavigator.exception;

/**
 * The request needs a signed-in account: no session, an expired one, or a wrong e-mail address or password.
 */
public final class UnauthorizedException extends DomainException {

    private static final long serialVersionUID = 1L;

    public UnauthorizedException(String message) {
        super(message);
    }
}
