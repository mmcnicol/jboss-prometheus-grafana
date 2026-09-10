package io.github.jpg.metrics.micrometer;

import io.github.jpg.metrics.Metrics;
import io.github.jpg.metrics.MetricsBackend;

/** Registers {@link MicrometerMetrics} via {@code META-INF/services}. */
public final class MicrometerBackend implements MetricsBackend {

    @Override
    public String name() {
        return "micrometer";
    }

    @Override
    public Metrics create() {
        return new MicrometerMetrics();
    }
}
