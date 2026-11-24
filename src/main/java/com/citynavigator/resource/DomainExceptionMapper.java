package com.citynavigator.resource;

import com.citynavigator.exception.BusinessRuleException;
import com.citynavigator.exception.ConflictException;
import com.citynavigator.exception.DomainException;
import com.citynavigator.exception.ResourceNotFoundException;
import com.citynavigator.exception.TooManyAttemptsException;
import com.citynavigator.exception.UnauthorizedException;
import com.citynavigator.exception.UnavailableException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Decides the HTTP status of each business failure; the exceptions themselves know nothing about HTTP.
 */
@Provider
public class DomainExceptionMapper implements ExceptionMapper<DomainException> {

    @Override
    public Response toResponse(DomainException exception) {
        return Problems.response(statusOf(exception), exception.getMessage());
    }

    static int statusOf(DomainException exception) {
        // DomainException is sealed, so the compiler checks that every kind has a status
        return switch (exception) {
            case ResourceNotFoundException notFound -> Response.Status.NOT_FOUND.getStatusCode();
            case ConflictException conflict -> Response.Status.CONFLICT.getStatusCode();
            case BusinessRuleException ruleBroken -> Problems.UNPROCESSABLE_CONTENT;
            case UnavailableException unavailable -> Response.Status.SERVICE_UNAVAILABLE.getStatusCode();
            case UnauthorizedException unauthorized -> Response.Status.UNAUTHORIZED.getStatusCode();
            case TooManyAttemptsException tooMany -> Response.Status.TOO_MANY_REQUESTS.getStatusCode();
        };
    }
}
