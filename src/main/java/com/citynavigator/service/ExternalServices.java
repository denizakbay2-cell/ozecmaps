package com.citynavigator.service;

import com.citynavigator.external.ExternalHttp;
import com.citynavigator.external.OsrmClient;
import com.citynavigator.external.PhotonClient;
import com.citynavigator.external.TrafficIndexClient;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;
import java.time.Duration;

/**
 * Configuration of the public services the application calls: nationwide routing and search, and Istanbul's live
 * traffic index. {@code EXTERNAL_SERVICES=false} switches them all off (the application then works with its own data
 * only); {@code ROUTING_SERVICE_URL}, {@code GEOCODER_URL} and {@code TRAFFIC_INDEX_URL} point to other servers, for
 * example self-hosted ones.
 */
@ApplicationScoped
public class ExternalServices {

    private boolean enabled;
    private ExternalHttp http;
    /** The traffic index is asked while a route is planned, so it gets less time. */
    private ExternalHttp quickHttp;

    @PostConstruct
    void configure() {
        enabled = !"false".equalsIgnoreCase(System.getenv("EXTERNAL_SERVICES"));
        http = new ExternalHttp(Duration.ofSeconds(15));
        quickHttp = new ExternalHttp(Duration.ofSeconds(4));
    }

    public boolean enabled() {
        return enabled;
    }

    @Produces
    @Singleton
    OsrmClient osrm(Clock clock) {
        return new OsrmClient(http, setting("ROUTING_SERVICE_URL", OsrmClient.FOSSGIS), clock);
    }

    @Produces
    @Singleton
    PhotonClient photon(Clock clock) {
        return new PhotonClient(http, setting("GEOCODER_URL", PhotonClient.KOMOOT), clock);
    }

    @Produces
    @Singleton
    TrafficIndexClient trafficIndex(Clock clock) {
        return new TrafficIndexClient(quickHttp, setting("TRAFFIC_INDEX_URL", TrafficIndexClient.IBB), clock);
    }

    private static String setting(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
