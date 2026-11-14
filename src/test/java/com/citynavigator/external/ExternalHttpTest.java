package com.citynavigator.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.citynavigator.routing.GeoPoint;
import com.citynavigator.routing.TravelMode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The clients against a real HTTP server on localhost that plays the public services.
 */
class ExternalHttpTest {

    private HttpServer server;
    private final List<String> userAgents = new CopyOnWriteArrayList<>();
    private final AtomicInteger routeCalls = new AtomicInteger();
    private final ExternalHttp http = new ExternalHttp(Duration.ofSeconds(5));

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/routed-car/", exchange -> {
            routeCalls.incrementAndGet();
            userAgents.add(exchange.getRequestHeaders().getFirst("User-Agent"));
            reply(exchange, 200, resource("external/osrm-route.json"));
        });
        server.createContext("/api/", exchange -> reply(exchange, 200, resource("external/photon-search.json")));
        server.createContext("/reverse", exchange -> reply(exchange, 200, resource("external/photon-search.json")));
        server.createContext("/missing", exchange -> reply(exchange, 400, "{\"code\":\"InvalidQuery\"}"));
        server.createContext("/broken", exchange -> reply(exchange, 502, "<html>busy</html>"));
        server.createContext("/html", exchange -> reply(exchange, 200, "<html>not json</html>"));
        server.createContext("/tkm/1/5M", exchange -> reply(exchange, 200,
                "[{\"TrafficIndex\":41,\"TrafficIndexDate\":\"2026-11-14T12:05:00\"}]"));
        server.createContext("/object", exchange -> reply(exchange, 200, "{\"TrafficIndex\":41}"));
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void routesIdentifyThemselvesAndAreCached() {
        OsrmClient client = new OsrmClient(http, base() + "/routed-{profile}/route/v1/driving/", Clock.systemUTC());
        GeoPoint pier = new GeoPoint(40.9917, 29.0227);
        GeoPoint moda = new GeoPoint(40.9813, 29.0262);

        ExternalRoute route = client.routes(pier, moda, TravelMode.CAR, 0).getFirst();
        client.routes(pier, moda, TravelMode.CAR, 0);

        assertThat(route.instructions()).hasSize(11);
        assertThat(routeCalls).hasValue(1);
        assertThat(userAgents).containsExactly(ExternalHttp.USER_AGENT);
    }

    @Test
    void searchesGoThroughTheGeocoder() {
        PhotonClient client = new PhotonClient(http, base(), Clock.systemUTC());
        assertThat(client.search("migros", null, 4)).hasSize(4);
        assertThat(client.reverse(new GeoPoint(40.99, 29.03))).get().extracting(GeocodedPlace::name).isEqualTo("Migros");
    }

    @Test
    void clientErrorsCarryTheirJsonAndOtherFailuresThrow() {
        ExternalHttp.JsonResponse missing = http.get(URI.create(base() + "/missing"));
        assertThat(missing.status()).isEqualTo(400);
        assertThat(missing.body().getString("code")).isEqualTo("InvalidQuery");
        assertThatThrownBy(() -> http.get(URI.create(base() + "/broken")))
                .isInstanceOf(ExternalServiceException.class).hasMessageContaining("HTTP 502");
        assertThatThrownBy(() -> http.get(URI.create(base() + "/html")))
                .isInstanceOf(ExternalServiceException.class).hasMessageContaining("JSON");
        assertThatThrownBy(() -> http.get(URI.create("http://127.0.0.1:1/nothing")))
                .isInstanceOf(ExternalServiceException.class).hasMessageContaining("could not be reached");
        PhotonClient geocoder = new PhotonClient(http, base() + "/missing?x=", Clock.systemUTC());
        assertThatThrownBy(() -> geocoder.search("x", null, 1)).isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void theTrafficIndexComesAsAJsonArray() {
        TrafficIndexClient client = new TrafficIndexClient(http, base() + "/tkm/", Clock.systemUTC());

        assertThat(client.current()).map(TrafficIndexClient.Reading::index).contains(41);
        assertThat(http.getArray(URI.create(base() + "/tkm/1/5M"))).hasSize(1);
        assertThatThrownBy(() -> http.getArray(URI.create(base() + "/object")))
                .isInstanceOf(ExternalServiceException.class).hasMessageContaining("JSON array");
        assertThatThrownBy(() -> http.getArray(URI.create(base() + "/missing")))
                .isInstanceOf(ExternalServiceException.class).hasMessageContaining("HTTP 400");
        assertThatThrownBy(() -> http.getArray(URI.create("http://127.0.0.1:1/nothing")))
                .isInstanceOf(ExternalServiceException.class).hasMessageContaining("could not be reached");
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String resource(String name) throws IOException {
        try {
            return Files.readString(Path.of(ExternalHttpTest.class.getClassLoader().getResource(name).toURI()));
        } catch (java.net.URISyntaxException e) {
            throw new IOException(e);
        }
    }

    private static void reply(com.sun.net.httpserver.HttpExchange exchange, int status, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
