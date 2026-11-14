package com.citynavigator.exception;

/**
 * Base type of every business failure. Sealed, so a switch over it must handle every kind.
 */
public abstract sealed class DomainException extends RuntimeException
        permits BusinessRuleException, ConflictException, ResourceNotFoundException, UnavailableException {

    private static final long serialVersionUID = 1L;

    protected DomainException(String message) {
        super(message);
    }
}
