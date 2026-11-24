package com.citynavigator.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.citynavigator.dto.ProblemDetail;
import com.citynavigator.exception.BusinessRuleException;
import com.citynavigator.exception.ConflictException;
import com.citynavigator.exception.ResourceNotFoundException;
import com.citynavigator.exception.TooManyAttemptsException;
import com.citynavigator.exception.UnauthorizedException;
import com.citynavigator.exception.UnavailableException;
import jakarta.json.bind.JsonbException;
import jakarta.persistence.OptimisticLockException;
import jakarta.transaction.RollbackException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ErrorHandlingTest {

    @Test
    void everyKindOfBusinessFailureHasItsStatus() {
        DomainExceptionMapper mapper = new DomainExceptionMapper();
        assertThat(mapper.toResponse(new ResourceNotFoundException("Olay", 1)).getStatus()).isEqualTo(404);
        assertThat(mapper.toResponse(new ConflictException("x")).getStatus()).isEqualTo(409);
        assertThat(mapper.toResponse(new UnavailableException("x")).getStatus()).isEqualTo(503);
        assertThat(mapper.toResponse(new UnauthorizedException("x")).getStatus()).isEqualTo(401);
        assertThat(mapper.toResponse(new TooManyAttemptsException("x")).getStatus()).isEqualTo(429);
        Response broken = mapper.toResponse(new BusinessRuleException("Varış noktası harita alanının dışında"));
        assertThat(broken.getStatus()).isEqualTo(422);
        assertThat(broken.getMediaType()).hasToString(Problems.MEDIA_TYPE);
        ProblemDetail body = (ProblemDetail) broken.getEntity();
        assertThat(body.title()).isEqualTo("Unprocessable Content");
        assertThat(body.detail()).isEqualTo("Varış noktası harita alanının dışında");
        assertThat(body.errors()).isNull();
    }

    @Test
    void validationFailuresListEachFieldByItsShortName() {
        ConstraintViolation<?> lat = violation("report.request.lat", "gerekli");
        ConstraintViolation<?> from = violation("route.from", "enlem,boylam biçiminde olmalı");
        Response response = new ConstraintViolationMapper().toResponse(
                new ConstraintViolationException(Set.of(lat, from)));

        ProblemDetail body = (ProblemDetail) response.getEntity();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(body.errors()).extracting(ProblemDetail.FieldError::field).containsExactly("from", "lat");
        assertThat(ConstraintViolationMapper.fieldOf(path("request"))).isEqualTo("request");
    }

    @Test
    void frameworkAndUnexpectedErrorsKeepTheProblemShape() {
        assertThat(new WebApplicationExceptionMapper().toResponse(new NotFoundException()).getStatus())
                .isEqualTo(404);
        UnexpectedExceptionMapper mapper = new UnexpectedExceptionMapper();
        assertThat(mapper.toResponse(new RuntimeException(new OptimisticLockException())).getStatus())
                .isEqualTo(409);
        assertThat(mapper.toResponse(new RollbackException("commit failed")).getStatus()).isEqualTo(500);
        assertThat(mapper.toResponse(new JsonbException("bad")).getStatus()).isEqualTo(400);
        Response hidden = mapper.toResponse(new IllegalStateException("secret detail"));
        assertThat(hidden.getStatus()).isEqualTo(500);
        assertThat(((ProblemDetail) hidden.getEntity()).detail()).doesNotContain("secret");
        assertThat(Problems.title(299)).isEqualTo("HTTP 299");
    }

    @Test
    void theAdminTokenGuardsChangesOnly() {
        ContainerRequestContext read = request("GET", null);
        new AdminTokenFilter("s3cret").filter(read);
        verify(read, never()).abortWith(any());

        ContainerRequestContext missing = request("POST", null);
        new AdminTokenFilter("s3cret").filter(missing);
        ArgumentCaptor<Response> refused = ArgumentCaptor.forClass(Response.class);
        verify(missing).abortWith(refused.capture());
        assertThat(refused.getValue().getStatus()).isEqualTo(401);

        ContainerRequestContext wrong = request("DELETE", "guess");
        new AdminTokenFilter("s3cret").filter(wrong);
        verify(wrong).abortWith(any());

        ContainerRequestContext right = request("POST", "s3cret");
        new AdminTokenFilter("s3cret").filter(right);
        verify(right, never()).abortWith(any());

        ContainerRequestContext open = request("POST", null);
        new AdminTokenFilter(" ").filter(open);
        verify(open, never()).abortWith(any());

        // accounts sign their requests in with their own token
        ContainerRequestContext account = request("PUT", null, "account/places");
        new AdminTokenFilter("s3cret").filter(account);
        verify(account, never()).abortWith(any());
    }

    @Test
    void answersAboutAnAccountAreNeverCached() {
        ContainerResponseContext response = mock(ContainerResponseContext.class);
        MultivaluedMap<String, Object> headers = new MultivaluedHashMap<>();
        when(response.getHeaders()).thenReturn(headers);

        new NoStoreForAccounts().filter(request("GET", null, "account/places"), response);
        assertThat(headers.getFirst("Cache-Control")).isEqualTo("no-store");

        headers.clear();
        new NoStoreForAccounts().filter(request("GET", null, "route"), response);
        assertThat(headers).isEmpty();
    }

    private static ContainerRequestContext request(String method, String token) {
        return request(method, token, "incidents");
    }

    private static ContainerRequestContext request(String method, String token, String path) {
        ContainerRequestContext request = mock(ContainerRequestContext.class);
        when(request.getMethod()).thenReturn(method);
        UriInfo uri = mock(UriInfo.class);
        org.mockito.Mockito.lenient().when(uri.getPath()).thenReturn(path);
        org.mockito.Mockito.lenient().when(request.getUriInfo()).thenReturn(uri);
        if (!"GET".equals(method)) {
            org.mockito.Mockito.lenient().when(request.getHeaderString(AdminTokenFilter.HEADER)).thenReturn(token);
        }
        return request;
    }

    @SuppressWarnings("unchecked")
    private static ConstraintViolation<?> violation(String path, String message) {
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path propertyPath = path(path);
        when(violation.getPropertyPath()).thenReturn(propertyPath);
        when(violation.getMessage()).thenReturn(message);
        return violation;
    }

    private static Path path(String dotted) {
        List<Path.Node> nodes = java.util.Arrays.stream(dotted.split("\\.")).map(name -> {
            Path.Node node = mock(Path.Node.class);
            when(node.getName()).thenReturn(name);
            return node;
        }).toList();
        return new Path() {
            @Override
            public Iterator<Node> iterator() {
                return nodes.iterator();
            }

            @Override
            public String toString() {
                return dotted;
            }
        };
    }
}
