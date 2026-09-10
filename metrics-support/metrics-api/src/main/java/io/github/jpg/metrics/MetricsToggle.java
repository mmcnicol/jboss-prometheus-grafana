package io.github.jpg.metrics;

/**
 * The single on/off switch for all instrumentation.
 *
 * <p>Controlled by the JVM system property {@code portal.metrics.enabled}
 * (default {@code false}). On WildFly / JBoss EAP set it in
 * {@code standalone.conf} / {@code JAVA_OPTS} or as a {@code <system-property>}
 * in {@code standalone.xml}, then restart. A restart is acceptable for
 * load-test environments (requirement FR2).
 *
 * <p>Read once and cached: the value cannot change without a restart, so callers
 * can treat {@link #isEnabled()} as constant.
 */
public final class MetricsToggle {

    /** System property name. */
    public static final String PROPERTY = "portal.metrics.enabled";

    private static final boolean ENABLED = readProperty();

    private MetricsToggle() {
    }

    /** Whether instrumentation should do any work. */
    public static boolean isEnabled() {
        return ENABLED;
    }

    private static boolean readProperty() {
        try {
            return Boolean.parseBoolean(System.getProperty(PROPERTY, "false"));
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * Evaluate the property fresh (ignoring the cached value). For tests only —
     * production code must use {@link #isEnabled()}.
     */
    static boolean readFresh() {
        return Boolean.parseBoolean(System.getProperty(PROPERTY, "false"));
    }
}
