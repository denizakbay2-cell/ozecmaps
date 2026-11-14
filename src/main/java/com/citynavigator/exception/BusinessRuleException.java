package com.citynavigator.exception;

/**
 * A rule was broken: a point outside the map, no road near a point, no route between two points.
 */
public final class BusinessRuleException extends DomainException {

    private static final long serialVersionUID = 1L;

    public BusinessRuleException(String message) {
        super(message);
    }
}
