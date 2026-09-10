package io.github.jpg.metrics;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class MetricsProviderTest {

    @Test
    void resolvesToNoOpWhenDisabled() {
        // property unset at class-init time -> disabled
        Metrics m = MetricsProvider.resolve();
        assertSame(NoOpMetrics.INSTANCE, m);
    }

    @Test
    void noOpTimerIsSharedAndSilent() {
        Metrics m = MetricsProvider.resolve();
        ActionTimer a = m.action("login");
        ActionTimer b = m.action("discharge.save");
        assertSame(a, b, "no-op timers should be the same shared instance");
        assertDoesNotThrow(() -> {
            a.record(Duration.ofMillis(5), ActionTimer.SUCCESS);
            a.record(null, ActionTimer.FAILURE);
            m.increment("whatever", "k", "v");
        });
    }

    @Test
    void metricsGetNeverReturnsNull() {
        assertNotNull(Metrics.get());
        assertNotNull(Metrics.get().action("x"));
    }
}
