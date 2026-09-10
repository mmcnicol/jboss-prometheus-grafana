package io.github.jpg.metrics;

import java.time.Duration;

/**
 * Does nothing. Used when metrics are disabled or no backend is available.
 * A single shared instance; {@link #action(String)} always returns the same
 * shared no-op timer, so instrumentation on the hot path allocates nothing.
 */
final class NoOpMetrics implements Metrics {

    static final NoOpMetrics INSTANCE = new NoOpMetrics();

    private static final ActionTimer TIMER = new ActionTimer() {
        @Override
        public void record(Duration elapsed, String outcome) {
            // no-op
        }
    };

    private NoOpMetrics() {
    }

    @Override
    public ActionTimer action(String actionName) {
        return TIMER;
    }

    @Override
    public void increment(String counter, String... labelPairs) {
        // no-op
    }
}
