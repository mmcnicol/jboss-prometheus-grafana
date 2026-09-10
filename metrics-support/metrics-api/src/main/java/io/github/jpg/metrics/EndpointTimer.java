package io.github.jpg.metrics;

import java.time.Duration;

/**
 * Records the elapsed time of one call to a service endpoint (the "middle"
 * load-test layer). Obtained from {@link Metrics#endpoint(String, String, String)}.
 *
 * <p>Like {@link ActionTimer}: thread-safe, never throws.
 */
public interface EndpointTimer {

    /**
     * Record one call.
     *
     * @param elapsed    wall-clock duration; ignored if null
     * @param httpStatus response status code (used to derive {@code outcome} and
     *                   {@code status} = {@code 2xx|3xx|4xx|5xx|other})
     */
    void record(Duration elapsed, int httpStatus);

    /** {@code success} for status &lt; 400, else {@code failure}. */
    static String outcomeFor(int status) {
        return status < 400 ? ActionTimer.SUCCESS : ActionTimer.FAILURE;
    }

    /** {@code 2xx|3xx|4xx|5xx|other}. */
    static String statusClass(int status) {
        if (status >= 200 && status < 300) {
            return "2xx";
        }
        if (status >= 300 && status < 400) {
            return "3xx";
        }
        if (status >= 400 && status < 500) {
            return "4xx";
        }
        if (status >= 500 && status < 600) {
            return "5xx";
        }
        return "other";
    }
}
