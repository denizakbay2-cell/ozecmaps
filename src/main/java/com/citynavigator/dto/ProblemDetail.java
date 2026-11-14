package com.citynavigator.dto;

import java.util.List;

/**
 * Error body in the RFC 9457 "Problem Details" shape ({@code application/problem+json}).
 * {@code errors} is present only for validation failures.
 */
public record ProblemDetail(String type, String title, int status, String detail, List<FieldError> errors) {

    public record FieldError(String field, String message) {
    }
}
