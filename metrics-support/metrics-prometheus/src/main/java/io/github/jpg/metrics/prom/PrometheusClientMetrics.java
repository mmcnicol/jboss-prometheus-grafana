package io.github.jpg.metrics.prom;

import io.github.jpg.metrics.ActionTimer;
import io.github.jpg.metrics.Metrics;
import io.github.jpg.metrics.MetricsScrape;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Histogram;
import io.prometheus.metrics.expositionformats.ExpositionFormats;
import io.prometheus.metrics.model.registry.PrometheusRegistry;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Prometheus-Java-client (client_java 1.x) implementation of the facade.
 *
 * <ul>
 *   <li>{@code portal_user_action_seconds} — one {@link Histogram} with
 *       {@code action} and {@code outcome} label names and classic buckets.</li>
 *   <li>{@code portal_events_total} — {@link Counter}s, one per distinct set of
 *       label names seen (the client needs label names fixed at registration —
 *       a Spike A ergonomics note).</li>
 * </ul>
 */
final class PrometheusClientMetrics implements Metrics {

    private static final double[] BUCKETS_SECONDS =
            {0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5, 10};

    private final PrometheusRegistry registry = new PrometheusRegistry();
    private final Histogram actionHistogram;
    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();

    PrometheusClientMetrics() {
        this.actionHistogram = Histogram.builder()
                .name("portal_user_action_seconds")
                .help("Elapsed time of a user action, by action and outcome")
                .labelNames("action", "outcome")
                .classicOnly()
                .classicUpperBounds(BUCKETS_SECONDS)
                .register(registry);
    }

    @Override
    public ActionTimer action(String actionName) {
        return (elapsed, outcome) -> {
            if (elapsed == null) {
                return;
            }
            String o = (outcome == null || outcome.isBlank()) ? "unknown" : outcome;
            actionHistogram.labelValues(actionName, o).observe(elapsed.toNanos() / 1_000_000_000.0);
        };
    }

    @Override
    public void increment(String event, String... labelPairs) {
        int pairs = labelPairs.length / 2;
        String[] names = new String[pairs + 1];
        String[] values = new String[pairs + 1];
        names[0] = "event";
        values[0] = event;
        for (int i = 0; i < pairs; i++) {
            names[i + 1] = labelPairs[2 * i];
            values[i + 1] = labelPairs[2 * i + 1];
        }
        counters.computeIfAbsent(String.join(",", names), key -> Counter.builder()
                        .name("portal_events")
                        .help("Application events")
                        .labelNames(names)
                        .register(registry))
                .labelValues(values).inc();
    }

    @Override
    public Optional<MetricsScrape> scrape() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ExpositionFormats.init().getPrometheusTextFormatWriter().write(out, registry.scrape());
            return Optional.of(new MetricsScrape(
                    "text/plain; version=0.0.4; charset=utf-8",
                    out.toString(StandardCharsets.UTF_8.name())));
        } catch (Exception e) {
            throw new IllegalStateException("failed to render Prometheus exposition", e);
        }
    }

    PrometheusRegistry registry() { // visible for tests
        return registry;
    }

    @Override
    public String toString() {
        return "PrometheusClientMetrics" + Arrays.toString(BUCKETS_SECONDS);
    }
}
