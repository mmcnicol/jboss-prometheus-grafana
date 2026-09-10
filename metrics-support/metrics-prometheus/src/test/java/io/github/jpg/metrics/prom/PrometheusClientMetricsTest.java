package io.github.jpg.metrics.prom;

import io.github.jpg.metrics.ActionTimer;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PrometheusClientMetricsTest {

    private static String scrape(PrometheusClientMetrics m) {
        return new String(m.scrape().orElseThrow().body(), StandardCharsets.UTF_8);
    }

    @Test
    void recordsActionHistogramWithActionAndOutcome() {
        PrometheusClientMetrics m = new PrometheusClientMetrics();
        m.action("login").record(Duration.ofMillis(150), ActionTimer.SUCCESS);
        m.action("login").record(Duration.ofMillis(900), ActionTimer.FAILURE);
        m.action("discharge.save").recordSuccess(Duration.ofMillis(80));

        String s = scrape(m);
        assertTrue(s.contains("portal_user_action_seconds_bucket"), s);
        assertTrue(s.contains("action=\"login\""), s);
        assertTrue(s.contains("outcome=\"success\""), s);
        assertTrue(s.contains("outcome=\"failure\""), s);
        assertTrue(s.contains("action=\"discharge.save\""), s);
    }

    @Test
    void nullElapsedIgnored() {
        PrometheusClientMetrics m = new PrometheusClientMetrics();
        m.action("login").record(null, ActionTimer.SUCCESS);
        assertTrue(!scrape(m).contains("action=\"login\""));
    }

    @Test
    void recordsEndpointHistogram() {
        PrometheusClientMetrics m = new PrometheusClientMetrics();
        m.endpoint("service-b", "/codes", "GET").record(Duration.ofMillis(12), 200);
        m.endpoint("service-b", "/validate", "POST").record(Duration.ofMillis(30), 400);

        String s = scrape(m);
        assertTrue(s.contains("service_endpoint_seconds_bucket"), s);
        assertTrue(s.contains("service=\"service-b\""), s);
        assertTrue(s.contains("route=\"/validate\""), s);
        assertTrue(s.contains("status=\"4xx\""), s);
    }

    @Test
    void incrementEmitsCounterTotal() {
        PrometheusClientMetrics m = new PrometheusClientMetrics();
        m.increment("login.attempt", "outcome", "denied");
        m.increment("login.attempt", "outcome", "denied");
        String s = scrape(m);
        assertTrue(s.contains("portal_events_total"), s);
        assertTrue(s.contains("event=\"login.attempt\""), s);
        assertTrue(s.contains("outcome=\"denied\""), s);
    }
}
