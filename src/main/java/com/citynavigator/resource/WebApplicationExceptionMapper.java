package com.citynavigator.resource;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Errors raised by JAX-RS itself (unknown path, wrong method, unreadable parameter) in the problem format.
 */
@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    @Override
    public Response toResponse(WebApplicationException exception) {
        return Problems.response(exception.getResponse().getStatus(), exception.getMessage());
    }
}
