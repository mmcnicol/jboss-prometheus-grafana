package io.github.jpg.metrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsToggleTest {

    @AfterEach
    void clearProperty() {
        System.clearProperty(MetricsToggle.PROPERTY);
    }

    @Test
    void defaultsToDisabled() {
        System.clearProperty(MetricsToggle.PROPERTY);
        assertFalse(MetricsToggle.readFresh());
    }

    @Test
    void enabledOnlyForExactlyTrue() {
        System.setProperty(MetricsToggle.PROPERTY, "true");
        assertTrue(MetricsToggle.readFresh());

        System.setProperty(MetricsToggle.PROPERTY, "TRUE");
        assertTrue(MetricsToggle.readFresh());

        System.setProperty(MetricsToggle.PROPERTY, "yes");
        assertFalse(MetricsToggle.readFresh());

        System.setProperty(MetricsToggle.PROPERTY, "1");
        assertFalse(MetricsToggle.readFresh());
    }

    @Test
    void cachedValueMatchesStartupProperty() {
        // The class was initialised with the property unset (see surefire config),
        // so the cached value must be false regardless of later changes.
        System.setProperty(MetricsToggle.PROPERTY, "true");
        assertFalse(MetricsToggle.isEnabled(), "isEnabled() must reflect the value read at class-init time");
    }
}
