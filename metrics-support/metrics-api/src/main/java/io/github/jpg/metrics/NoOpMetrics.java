package io.github.jpg.metrics;

/**
 * Does nothing. Used when metrics are disabled or no backend is available.
 * Shared instances so instrumentation on the hot path allocates nothing.
 */
final class NoOpMetrics implements Metrics {

    static final NoOpMetrics INSTANCE = new NoOpMetrics();

    private static final ActionTimer TIMER = (elapsed, outcome) -> {
        // no-op
    };

    private static final EndpointTimer ENDPOINT_TIMER = (elapsed, httpStatus) -> {
        // no-op
    };

    private NoOpMetrics() {
    }

    @Override
    public ActionTimer action(String actionName) {
        return TIMER;
    }

    @Override
    public EndpointTimer endpoint(String service, String route, String method) {
        return ENDPOINT_TIMER;
    }

    @Override
    public void increment(String counter, String... labelPairs) {
        // no-op
    }
}
