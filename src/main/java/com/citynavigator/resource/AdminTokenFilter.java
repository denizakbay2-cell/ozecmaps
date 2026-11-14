package com.citynavigator.resource;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

/**
 * Keeps a public deployment read-only for strangers: when {@code ADMIN_TOKEN} is set, every request that changes
 * data (reporting an incident, adding a place) must carry it in the {@code X-Admin-Token} header. Routing and
 * searching stay open. Without the variable (local development) everything is open.
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AdminTokenFilter implements ContainerRequestFilter {

    static final String HEADER = "X-Admin-Token";
    private static final Set<String> READ_ONLY_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final byte[] expectedToken;

    public AdminTokenFilter() {
        this(System.getenv("ADMIN_TOKEN"));
    }

    AdminTokenFilter(String token) {
        this.expectedToken = token == null || token.isBlank() ? null : token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void filter(ContainerRequestContext request) {
        if (expectedToken == null || READ_ONLY_METHODS.contains(request.getMethod())) {
            return;
        }
        String given = request.getHeaderString(HEADER);
        // constant-time comparison, so the token cannot be guessed from response times
        if (given == null || !MessageDigest.isEqual(expectedToken, given.getBytes(StandardCharsets.UTF_8))) {
            request.abortWith(Problems.response(Response.Status.UNAUTHORIZED.getStatusCode(),
                    "Değişiklik için geçerli bir " + HEADER + " başlığı gerekiyor"));
        }
    }
}
