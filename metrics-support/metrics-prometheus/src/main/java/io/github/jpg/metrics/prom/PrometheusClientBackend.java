package io.github.jpg.metrics.prom;

import io.github.jpg.metrics.Metrics;
import io.github.jpg.metrics.MetricsBackend;

/** Registers {@link PrometheusClientMetrics} via {@code META-INF/services}. */
public final class PrometheusClientBackend implements MetricsBackend {

    @Override
    public String name() {
        return "prometheus-client";
    }

    @Override
    public Metrics create() {
        return new PrometheusClientMetrics();
    }
}
