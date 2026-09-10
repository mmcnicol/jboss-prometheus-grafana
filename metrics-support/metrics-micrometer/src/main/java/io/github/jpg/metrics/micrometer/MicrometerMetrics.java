package io.github.jpg.metrics.micrometer;

import io.github.jpg.metrics.ActionTimer;
import io.github.jpg.metrics.Metrics;
import io.github.jpg.metrics.MetricsScrape;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Micrometer implementation of the facade.
 *
 * <ul>
 *   <li>{@code portal_user_action_seconds} — a {@link Timer} per (action, outcome),
 *       with a percentile histogram so Grafana can do {@code histogram_quantile}.</li>
 *   <li>{@code portal_events_total} — counters via {@link #increment}.</li>
 * </ul>
 */
final class MicrometerMetrics implements Metrics {

    private static final String ACTION_METRIC = "portal.user.action";
    private static final String EVENT_METRIC = "portal.events";

    private final PrometheusMeterRegistry registry;
    private final ConcurrentMap<String, ActionTimer> actions = new ConcurrentHashMap<>();

    MicrometerMetrics() {
        this.registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    }

    @Override
    public ActionTimer action(String actionName) {
        return actions.computeIfAbsent(actionName, MicrometerActionTimer::new);
    }

    @Override
    public void increment(String counter, String... labelPairs) {
        Counter.Builder b = Counter.builder(EVENT_METRIC).tag("event", counter);
        for (int i = 0; i + 1 < labelPairs.length; i += 2) {
            b.tag(labelPairs[i], labelPairs[i + 1]);
        }
        b.register(registry).increment();
    }

    @Override
    public Optional<MetricsScrape> scrape() {
        return Optional.of(new MetricsScrape(
                "text/plain; version=0.0.4; charset=utf-8",
                registry.scrape()));
    }

    PrometheusMeterRegistry registry() { // visible for tests
        return registry;
    }

    /** One per action name; caches the Timer for each outcome it has seen. */
    private final class MicrometerActionTimer implements ActionTimer {

        private final String action;
        private final ConcurrentMap<String, Timer> byOutcome = new ConcurrentHashMap<>();

        MicrometerActionTimer(String action) {
            this.action = action;
        }

        @Override
        public void record(Duration elapsed, String outcome) {
            if (elapsed == null) {
                return;
            }
            String o = (outcome == null || outcome.isBlank()) ? "unknown" : outcome;
            byOutcome.computeIfAbsent(o, this::buildTimer).record(elapsed);
        }

        private Timer buildTimer(String outcome) {
            return Timer.builder(ACTION_METRIC)
                    .tag("action", action)
                    .tag("outcome", outcome)
                    .publishPercentileHistogram()
                    .minimumExpectedValue(Duration.ofMillis(1))
                    .maximumExpectedValue(Duration.ofSeconds(10))
                    .register(registry);
        }
    }
}
