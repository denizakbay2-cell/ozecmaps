package com.citynavigator.resource;

import com.citynavigator.dto.ProblemDetail;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Bean Validation failures on bodies and parameters become a 400 listing every invalid field.
 */
@Provider
public class ConstraintViolationMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<ProblemDetail.FieldError> errors = exception.getConstraintViolations().stream()
                .map(violation -> new ProblemDetail.FieldError(fieldOf(violation.getPropertyPath()),
                        violation.getMessage()))
                .sorted(Comparator.comparing(ProblemDetail.FieldError::field))
                .toList();
        return Problems.response(Response.Status.BAD_REQUEST.getStatusCode(), "İstek geçersiz", errors);
    }

    /**
     * Drops the resource method and parameter from the path: {@code report.request.lat} becomes {@code lat}.
     * A constraint on a parameter itself (a missing body, a query parameter) is reported under the parameter name.
     */
    static String fieldOf(Path path) {
        List<String> names = new ArrayList<>();
        path.forEach(node -> names.add(node.getName()));
        String full = path.toString();
        if (names.size() < 2) {
            return full;
        }
        String methodAndParameter = names.get(0) + "." + names.get(1);
        return full.startsWith(methodAndParameter + ".")
                ? full.substring(methodAndParameter.length() + 1)
                : names.get(1);
    }
}
