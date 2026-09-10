package io.github.jpg.metrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link MetricsToggle#readFresh()} — the property-parsing logic.
 *
 * <p>{@link MetricsToggle#isEnabled()} caches its value at class-init time and so
 * cannot be exercised for both states within one JVM; that behaviour is covered
 * by the deploy-time checks in {@code docs/} (metrics on vs off), not here.
 */
class MetricsToggleTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty(MetricsToggle.PROPERTY);
    }

    @Test
    void defaultsToDisabledWhenUnset() {
        System.clearProperty(MetricsToggle.PROPERTY);
        assertFalse(MetricsToggle.readFresh());
    }

    @Test
    void trueIsCaseInsensitive() {
        System.setProperty(MetricsToggle.PROPERTY, "true");
        assertTrue(MetricsToggle.readFresh());
        System.setProperty(MetricsToggle.PROPERTY, "TRUE");
        assertTrue(MetricsToggle.readFresh());
        System.setProperty(MetricsToggle.PROPERTY, "True");
        assertTrue(MetricsToggle.readFresh());
    }

    @Test
    void nonTrueValuesAreDisabled() {
        for (String v : new String[] {"false", "yes", "1", "on", "", "  "}) {
            System.setProperty(MetricsToggle.PROPERTY, v);
            assertFalse(MetricsToggle.readFresh(), "value '" + v + "' should be disabled");
        }
    }
}
