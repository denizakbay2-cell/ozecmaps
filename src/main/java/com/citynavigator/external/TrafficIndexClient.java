package com.citynavigator.external;

import com.citynavigator.routing.DayTime;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The live traffic index of Istanbul from Istanbul Metropolitan Municipality's open data service: one number from
 * 1 (open roads) to 99 (very heavy traffic) for the whole city, every five minutes. Answers are cached, and after a
 * failure the service is left alone for a while, so a slow or broken service cannot hold up route requests.
 */
public final class TrafficIndexClient {

    public static final String IBB = "https://api.ibb.gov.tr/tkmservices/api/TrafficData/v1/TrafficIndexHistory/";
    /** The service publishes a new value every five minutes. */
    static final Duration LATEST_TTL = Duration.ofMinutes(5);
    static final Duration USUAL_TTL = Duration.ofHours(12);
    static final Duration BACK_OFF = Duration.ofMinutes(2);

    private final ExternalHttp http;
    private final String baseUrl;
    private final Clock clock;
    private final TtlCache<String, Optional<Reading>> latest;
    private final TtlCache<String, double[]> usual;
    private volatile Instant quietUntil = Instant.MIN;

    public TrafficIndexClient(ExternalHttp http, String baseUrl, Clock clock) {
        this.http = http;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.clock = clock;
        this.latest = new TtlCache<>(1, LATEST_TTL, clock);
        this.usual = new TtlCache<>(1, USUAL_TTL, clock);
    }

    /**
     * The most recent index value, if the service has one.
     */
    public Optional<Reading> current() {
        return latest.computeIfAbsent("latest", () -> guarded(() -> newest(fetch("1/5M"))));
    }

    /**
     * The average index of the last four weeks at that day type and hour, or {@code NaN} if unknown.
     */
    public double typical(DayTime when) {
        return usual.computeIfAbsent("usual", () -> guarded(() -> averages(fetch("28/H"))))[when.slot()];
    }

    private JsonArray fetch(String period) {
        return http.getArray(URI.create(baseUrl + period));
    }

    private <T> T guarded(Supplier<T> call) {
        if (clock.instant().isBefore(quietUntil)) {
            throw new ExternalServiceException("the traffic index service failed recently; not asking again yet");
        }
        try {
            return call.get();
        } catch (ExternalServiceException e) {
            quietUntil = clock.instant().plus(BACK_OFF);
            throw e;
        }
    }

    static Optional<Reading> newest(JsonArray values) {
        Reading newest = null;
        for (JsonValue value : values) {
            Reading reading = reading(value.asJsonObject());
            if (reading != null && (newest == null || reading.at().isAfter(newest.at()))) {
                newest = reading;
            }
        }
        return Optional.ofNullable(newest);
    }

    static double[] averages(JsonArray values) {
        double[] sum = new double[DayTime.DAY_TYPES * 24];
        int[] count = new int[sum.length];
        for (JsonValue value : values) {
            Reading reading = reading(value.asJsonObject());
            if (reading != null) {
                int slot = DayTime.of(reading.at()).slot();
                sum[slot] += reading.index();
                count[slot]++;
            }
        }
        double[] average = new double[sum.length];
        Arrays.setAll(average, slot -> count[slot] == 0 ? Double.NaN : sum[slot] / count[slot]);
        return average;
    }

    private static Reading reading(JsonObject json) {
        if (!json.containsKey("TrafficIndex") || !json.containsKey("TrafficIndexDate")
                || json.get("TrafficIndex").getValueType() != JsonValue.ValueType.NUMBER) {
            return null;
        }
        return new Reading(json.getInt("TrafficIndex"), LocalDateTime.parse(json.getString("TrafficIndexDate")));
    }

    /**
     * @param at Istanbul local time of the reading
     */
    public record Reading(int index, LocalDateTime at) {
    }
}
