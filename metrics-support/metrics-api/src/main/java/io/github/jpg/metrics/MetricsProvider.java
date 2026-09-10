package io.github.jpg.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Chooses the {@link Metrics} implementation at startup.
 *
 * <ul>
 *   <li>metrics disabled ({@link MetricsToggle}) &rarr; {@link NoOpMetrics}</li>
 *   <li>enabled and exactly one {@link MetricsBackend} on the classpath &rarr; that backend</li>
 *   <li>enabled but zero or several backends &rarr; {@link NoOpMetrics}, with a warning</li>
 * </ul>
 *
 * <p>Phase 1 adds the Micrometer and Prometheus-client backends as
 * {@link MetricsBackend} services; Phase 0 ships only the no-op.
 */
final class MetricsProvider {

    private static final Logger LOG = Logger.getLogger(MetricsProvider.class.getName());

    private MetricsProvider() {
    }

    static Metrics resolve() {
        if (!MetricsToggle.isEnabled()) {
            LOG.info(() -> MetricsToggle.PROPERTY + "=false — metrics disabled (no-op)");
            return NoOpMetrics.INSTANCE;
        }

        List<MetricsBackend> backends = new ArrayList<>();
        try {
            for (MetricsBackend backend : ServiceLoader.load(MetricsBackend.class)) {
                backends.add(backend);
            }
        } catch (ServiceConfigurationError | RuntimeException e) {
            LOG.log(Level.WARNING, "failed to load a metrics backend; falling back to no-op", e);
            return NoOpMetrics.INSTANCE;
        }

        if (backends.size() == 1) {
            MetricsBackend chosen = backends.get(0);
            LOG.info("metrics enabled — backend: " + chosen.name());
            try {
                return chosen.create();
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "backend " + chosen.name() + " failed to start; using no-op", e);
                return NoOpMetrics.INSTANCE;
            }
        }
        if (backends.isEmpty()) {
            LOG.warning(MetricsToggle.PROPERTY + "=true but no MetricsBackend on the classpath — using no-op");
        } else {
            LOG.warning(backends.size() + " MetricsBackend implementations found; expected exactly one — using no-op");
        }
        return NoOpMetrics.INSTANCE;
    }
}
