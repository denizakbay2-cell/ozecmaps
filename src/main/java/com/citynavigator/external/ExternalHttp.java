package com.citynavigator.external;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * JSON over HTTP GET to third-party services, with the identifying User-Agent their policies require, timeouts, and
 * failures turned into {@link ExternalServiceException}.
 */
public final class ExternalHttp {

    static final String USER_AGENT = "city-navigator/0.1 (Jakarta EE course project)";

    private final HttpClient client;
    private final Duration timeout;

    public ExternalHttp(Duration timeout) {
        this.timeout = timeout;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * The status and JSON body of a GET. Bodies of client errors (4xx) are returned too, because services such as
     * OSRM explain the problem in them; other failures throw.
     */
    public JsonResponse get(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                int status = response.statusCode();
                if (status != 200 && (status < 400 || status >= 500)) {
                    throw new ExternalServiceException(uri.getHost() + " answered HTTP " + status);
                }
                try (JsonReader reader = Json.createReader(body)) {
                    return new JsonResponse(status, reader.readObject());
                }
            }
        } catch (IOException e) {
            throw new ExternalServiceException(uri.getHost() + " could not be reached", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("interrupted while calling " + uri.getHost(), e);
        } catch (JsonException e) {
            throw new ExternalServiceException(uri.getHost() + " did not answer with JSON", e);
        }
    }

    /**
     * A GET that must succeed and answer with a JSON array.
     */
    public JsonArray getArray(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();
        try {
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body(); JsonReader reader = Json.createReader(body)) {
                if (response.statusCode() != 200) {
                    throw new ExternalServiceException(uri.getHost() + " answered HTTP " + response.statusCode());
                }
                return reader.readArray();
            }
        } catch (IOException e) {
            throw new ExternalServiceException(uri.getHost() + " could not be reached", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("interrupted while calling " + uri.getHost(), e);
        } catch (JsonException e) {
            throw new ExternalServiceException(uri.getHost() + " did not answer with a JSON array", e);
        }
    }

    public record JsonResponse(int status, JsonObject body) {
    }
}
