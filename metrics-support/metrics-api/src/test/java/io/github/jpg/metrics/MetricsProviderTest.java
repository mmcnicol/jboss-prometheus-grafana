package io.github.jpg.metrics;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * This module ships no {@link MetricsBackend}, so {@link MetricsProvider#resolve()}
 * always yields the no-op — regardless of the toggle. That is exactly what we
 * assert here. Backend selection with the toggle on is covered in Phase 1 when
 * the Micrometer / Prometheus-client backends exist.
 */
class MetricsProviderTest {

    @Test
    void resolvesToNoOpWhenNoBackendPresent() {
        assertSame(NoOpMetrics.INSTANCE, MetricsProvider.resolve());
    }

    @Test
    void noOpTimerIsSharedAndSilent() {
        Metrics m = MetricsProvider.resolve();
        ActionTimer a = m.action("login");
        ActionTimer b = m.action("discharge.save");
        assertSame(a, b, "no-op timers should be one shared instance");
        assertDoesNotThrow(() -> {
            a.record(Duration.ofMillis(5), ActionTimer.SUCCESS);
            a.record(null, ActionTimer.FAILURE);
            m.endpoint("svc", "/x", "GET").record(Duration.ofMillis(1), 200);
            m.increment("whatever", "k", "v");
        });
    }

    @Test
    void metricsGetNeverReturnsNull() {
        assertNotNull(Metrics.get());
        assertNotNull(Metrics.get().action("x"));
    }
}
