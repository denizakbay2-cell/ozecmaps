package com.citynavigator.resource;

import com.citynavigator.dto.ProblemDetail;
import jakarta.ws.rs.core.Response;
import java.util.List;

/**
 * Builds {@code application/problem+json} responses (RFC 9457), so every error has the same shape.
 */
final class Problems {

    static final String MEDIA_TYPE = "application/problem+json";
    static final int UNPROCESSABLE_CONTENT = 422;

    private Problems() {
    }

    static Response response(int status, String detail) {
        return response(status, detail, null);
    }

    static Response response(int status, String detail, List<ProblemDetail.FieldError> errors) {
        return Response.status(status)
                .type(MEDIA_TYPE)
                .entity(new ProblemDetail("about:blank", title(status), status, detail, errors))
                .build();
    }

    static String title(int status) {
        if (status == UNPROCESSABLE_CONTENT) {
            return "Unprocessable Content";
        }
        Response.Status known = Response.Status.fromStatusCode(status);
        return known == null ? "HTTP " + status : known.getReasonPhrase();
    }
}
