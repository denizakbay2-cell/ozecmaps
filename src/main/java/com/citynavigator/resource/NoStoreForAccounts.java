package com.citynavigator.resource;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.ext.Provider;

/**
 * Answers about an account (its places include a home address) are never kept by a browser cache or a proxy such
 * as the CDN in front of the application.
 */
@Provider
public class NoStoreForAccounts implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (request.getUriInfo().getPath().startsWith(AdminTokenFilter.ACCOUNTS)) {
            response.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, "no-store");
        }
    }
}
