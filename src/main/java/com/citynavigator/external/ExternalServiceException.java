package com.citynavigator.external;

/**
 * A third-party service did not give a usable answer: unreachable, too slow, busy, or an error.
 */
public class ExternalServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
