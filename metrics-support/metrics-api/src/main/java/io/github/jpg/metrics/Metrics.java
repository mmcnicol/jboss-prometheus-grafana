package io.github.jpg.metrics;

/**
 * Entry point to the timing facade. Application code depends only on this
 * interface and {@link ActionTimer}; the concrete backend is chosen at startup.
 *
 * <p>Typical use:
 * <pre>{@code
 * long start = System.nanoTime();
 * String outcome = ActionTimer.SUCCESS;
 * try {
 *     doTheThing();
 * } catch (RuntimeException e) {
 *     outcome = ActionTimer.FAILURE;
 *     throw e;
 * } finally {
 *     Metrics.get().action("login").record(Duration.ofNanos(System.nanoTime() - start), outcome);
 * }
 * }</pre>
 *
 * <p>When metrics are disabled (the default — see {@link MetricsToggle}),
 * {@link #get()} returns a no-op instance that allocates nothing and does
 * nothing, so leaving these calls in the code path is cheap.
 */
public interface Metrics {

    /**
     * Return the timer for {@code actionName}, creating it on first use.
     * The same instance is returned for the same name.
     *
     * @param actionName stable, low-cardinality identifier (e.g. {@code discharge.save})
     */
    ActionTimer action(String actionName);

    /**
     * Return the timer for a service endpoint (the "middle" load-test layer),
     * emitting {@code service_endpoint_seconds{service,route,method,status,outcome}}.
     *
     * @param service low-cardinality service name (e.g. {@code service-a})
     * @param route   the matched path template (e.g. {@code /patients/{ref}})
     * @param method  HTTP method
     */
    EndpointTimer endpoint(String service, String route, String method);

    /**
     * Increment a named counter, optionally with {@code key, value, key, value...}
     * label pairs.
     */
    void increment(String counter, String... labelPairs);

    /**
     * Render the current metrics for the scrape endpoint. Empty when metrics are
     * disabled or the backend has no HTTP exposition — the {@code /metrics}
     * endpoint then responds 404.
     */
    default java.util.Optional<MetricsScrape> scrape() {
        return java.util.Optional.empty();
    }

    /**
     * The configured backend, or a no-op instance when metrics are disabled or
     * no backend is on the classpath.
     */
    static Metrics get() {
        return Holder.INSTANCE;
    }

    /** Lazy holder so backend resolution happens once, on first use. */
    final class Holder {
        static final Metrics INSTANCE = MetricsProvider.resolve();

        private Holder() {
        }
    }
}
