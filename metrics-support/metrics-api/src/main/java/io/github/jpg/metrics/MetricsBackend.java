package io.github.jpg.metrics;

/**
 * SPI for a metrics backend. Discovered via {@link java.util.ServiceLoader}
 * when {@code portal.metrics.enabled=true}.
 *
 * <p>A backend module (e.g. {@code metrics-micrometer}, {@code metrics-prometheus})
 * provides one implementation and registers it in
 * {@code META-INF/services/io.github.jpg.metrics.MetricsBackend}. Exactly one
 * backend is expected on the classpath at a time (requirement FR7: the two
 * implementations are compared, only one ships as the default).
 */
public interface MetricsBackend {

    /** Short name for logs, e.g. {@code micrometer} or {@code prometheus-client}. */
    String name();

    /** Build the {@link Metrics} instance. Called once. */
    Metrics create();
}
