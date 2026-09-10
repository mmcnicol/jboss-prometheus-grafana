package io.github.jpg.metrics.micrometer;

import io.github.jpg.metrics.ActionTimer;
import io.github.jpg.metrics.MetricsScrape;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicrometerMetricsTest {

    @Test
    void recordsActionTimerWithActionAndOutcomeTags() {
        MicrometerMetrics m = new MicrometerMetrics();
        m.action("login").record(Duration.ofMillis(150), ActionTimer.SUCCESS);
        m.action("login").record(Duration.ofMillis(400), ActionTimer.FAILURE);
        m.action("discharge.save").recordSuccess(Duration.ofMillis(90));

        String scrape = new String(m.scrape().orElseThrow().body(), StandardCharsets.UTF_8);
        assertTrue(scrape.contains("portal_user_action_seconds"), scrape);
        assertTrue(scrape.contains("action=\"login\""), scrape);
        assertTrue(scrape.contains("outcome=\"success\""), scrape);
        assertTrue(scrape.contains("outcome=\"failure\""), scrape);
        assertTrue(scrape.contains("action=\"discharge.save\""), scrape);
        // percentile histogram buckets present -> histogram_quantile works in Grafana
        assertTrue(scrape.contains("portal_user_action_seconds_bucket"), scrape);
    }

    @Test
    void sameActionNameReturnsSameTimer() {
        MicrometerMetrics m = new MicrometerMetrics();
        assertSame(m.action("x"), m.action("x"));
    }

    @Test
    void nullElapsedIsIgnored() {
        MicrometerMetrics m = new MicrometerMetrics();
        m.action("login").record(null, ActionTimer.SUCCESS);
        String scrape = new String(m.scrape().orElseThrow().body(), StandardCharsets.UTF_8);
        assertTrue(!scrape.contains("action=\"login\""));
    }

    @Test
    void recordsEndpointTimerWithServiceRouteMethodStatus() {
        MicrometerMetrics m = new MicrometerMetrics();
        m.endpoint("service-a", "/patients/{ref}", "GET").record(Duration.ofMillis(20), 200);
        m.endpoint("service-a", "/patients", "POST").record(Duration.ofMillis(50), 500);

        String s = new String(m.scrape().orElseThrow().body(), StandardCharsets.UTF_8);
        assertTrue(s.contains("service_endpoint_seconds"), s);
        assertTrue(s.contains("service=\"service-a\""), s);
        assertTrue(s.contains("route=\"/patients/{ref}\""), s);
        assertTrue(s.contains("method=\"GET\""), s);
        assertTrue(s.contains("status=\"2xx\""), s);
        assertTrue(s.contains("status=\"5xx\""), s);
        assertTrue(s.contains("outcome=\"failure\""), s);
    }

    @Test
    void incrementEmitsCounter() {
        MicrometerMetrics m = new MicrometerMetrics();
        m.increment("login.attempt", "outcome", "denied");
        String scrape = new String(m.scrape().orElseThrow().body(), StandardCharsets.UTF_8);
        assertTrue(scrape.contains("portal_events_total"), scrape);
        assertTrue(scrape.contains("event=\"login.attempt\""), scrape);
    }

    @Test
    void scrapeContentTypeIsPrometheusText() {
        MetricsScrape s = new MicrometerMetrics().scrape().orElseThrow();
        assertNotNull(s.contentType());
        assertTrue(s.contentType().startsWith("text/plain"));
    }
}
