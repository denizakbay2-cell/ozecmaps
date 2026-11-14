package com.citynavigator.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.citynavigator.routing.DayTime;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import java.io.StringReader;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class TrafficIndexClientTest {

    private static final String LAST_DAY = """
            [{"TrafficIndex":69,"TrafficIndexDate":"2026-11-14T16:07:00"},
             {"TrafficIndex":72,"TrafficIndexDate":"2026-11-14T16:17:00"},
             {"TrafficIndex":"?","TrafficIndexDate":"2026-11-14T16:22:00"},
             {"TrafficIndexDate":"2026-11-14T16:27:00"},
             {"TrafficIndex":71,"TrafficIndexDate":"2026-11-14T16:12:00"}]""";
    private static final String FOUR_WEEKS = """
            [{"TrafficIndex":70,"TrafficIndexDate":"2026-11-14T16:00:00"},
             {"TrafficIndex":50,"TrafficIndexDate":"2026-11-07T16:00:00"},
             {"TrafficIndex":64,"TrafficIndexDate":"2026-11-13T16:00:00"}]""";

    private final ExternalHttp http = mock(ExternalHttp.class);
    private final MutableClock clock = new MutableClock();
    private final TrafficIndexClient client = new TrafficIndexClient(http, "https://example.test/history", clock);

    @Test
    void takesTheNewestReadingWhateverTheOrderAndSkipsBrokenOnes() {
        assertThat(TrafficIndexClient.newest(array(LAST_DAY)))
                .contains(new TrafficIndexClient.Reading(72, LocalDateTime.parse("2026-11-14T16:17:00")));
        assertThat(TrafficIndexClient.newest(array("[]"))).isEmpty();
    }

    @Test
    void averagesTheLastWeeksByDayTypeAndHour() {
        double[] averages = TrafficIndexClient.averages(array(FOUR_WEEKS));

        assertThat(averages[new DayTime(DayTime.SATURDAY, 16).slot()]).isEqualTo(60);
        assertThat(averages[new DayTime(DayTime.WEEKDAY, 16).slot()]).isEqualTo(64);
        assertThat(averages[new DayTime(DayTime.SUNDAY, 16).slot()]).isNaN();
    }

    @Test
    void asksTheServiceAgainOnlyWhenItsAnswerIsOld() {
        when(http.getArray(URI.create("https://example.test/history/1/5M"))).thenReturn(array(LAST_DAY));
        when(http.getArray(URI.create("https://example.test/history/28/H"))).thenReturn(array(FOUR_WEEKS));

        assertThat(client.current()).map(TrafficIndexClient.Reading::index).contains(72);
        client.current();
        assertThat(client.typical(new DayTime(DayTime.SATURDAY, 16))).isEqualTo(60);
        assertThat(client.typical(new DayTime(DayTime.SUNDAY, 16))).isNaN();
        verify(http, times(2)).getArray(any());

        clock.now = clock.now.plus(TrafficIndexClient.LATEST_TTL);
        client.current();
        client.typical(new DayTime(DayTime.SATURDAY, 16));
        verify(http, times(3)).getArray(any());
    }

    @Test
    void leavesAFailingServiceAloneForAWhile() {
        when(http.getArray(any())).thenThrow(new ExternalServiceException("down"));

        assertThatThrownBy(client::current).isInstanceOf(ExternalServiceException.class).hasMessage("down");
        assertThatThrownBy(client::current).hasMessageContaining("not asking again");
        assertThatThrownBy(() -> client.typical(new DayTime(DayTime.WEEKDAY, 8)))
                .hasMessageContaining("not asking again");
        verify(http, times(1)).getArray(any());

        clock.now = clock.now.plus(TrafficIndexClient.BACK_OFF);
        assertThatThrownBy(client::current).hasMessage("down");
        verify(http, times(2)).getArray(any());
    }

    static JsonArray array(String json) {
        try (var reader = Json.createReader(new StringReader(json))) {
            return reader.readArray();
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-14T13:20:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
