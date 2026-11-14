package com.citynavigator.resource;

import jakarta.json.bind.JsonbException;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Last line of defence: recognises a few infrastructure failures and hides everything else behind a 500, so
 * stack traces never reach the client.
 */
@Provider
public class UnexpectedExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER = Logger.getLogger(UnexpectedExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable exception) {
        if (causedBy(exception, OptimisticLockException.class)) {
            return Problems.response(Response.Status.CONFLICT.getStatusCode(),
                    "Kayıt bu arada başka biri tarafından değiştirildi; yenileyip tekrar deneyin");
        }
        if (causedBy(exception, JsonbException.class)) {
            return Problems.response(Response.Status.BAD_REQUEST.getStatusCode(),
                    "İstek gövdesi bu uç nokta için geçerli bir JSON değil");
        }
        LOGGER.log(Level.SEVERE, "unexpected error while handling a request", exception);
        return Problems.response(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), "Beklenmeyen bir hata oluştu");
    }

    static boolean causedBy(Throwable exception, Class<? extends Throwable> type) {
        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }
}
