package io.github.jpg.metrics;

import java.time.Duration;

/**
 * Records the elapsed time of one occurrence of a named user action
 * (e.g. {@code login}, {@code discharge.save}).
 *
 * <p>Implementations must be safe to call from many threads and must never throw:
 * a metrics failure must not affect the request that triggered it (see
 * requirement NFR2).
 */
public interface ActionTimer {

    /** Outcome label value for an action that completed normally. */
    String SUCCESS = "success";
    /** Outcome label value for an action that failed. */
    String FAILURE = "failure";

    /**
     * Record one timing.
     *
     * @param elapsed wall-clock duration of the action; ignored if null
     * @param outcome {@link #SUCCESS}, {@link #FAILURE}, or any short label
     */
    void record(Duration elapsed, String outcome);

    /** Convenience: record a success. */
    default void recordSuccess(Duration elapsed) {
        record(elapsed, SUCCESS);
    }

    /** Convenience: record a failure. */
    default void recordFailure(Duration elapsed) {
        record(elapsed, FAILURE);
    }
}
