package io.github.jpg.load;

import org.openqa.selenium.WebDriver;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Runs {@link PortalScenario} in a loop across {@code VUS} browsers for
 * {@code DURATION_SECONDS}. Configuration via system properties (all optional):
 *
 * <pre>
 *   -DbaseUrl=http://localhost:8080/portal-web   (required in practice)
 *   -Dvus=2
 *   -DdurationSeconds=60
 *   -DthinkMillis=500
 * </pre>
 *
 * The run's identity ({@code run_id}, {@code release}) is attached to metrics by
 * the OTel Collector, not here (decision: labels applied outside the app).
 */
public final class LoadRunner {

    public static void main(String[] args) throws InterruptedException {
        String baseUrl = prop("baseUrl", "http://localhost:8080/portal-web");
        int vus = Integer.parseInt(prop("vus", "2"));
        int durationSeconds = Integer.parseInt(prop("durationSeconds", "60"));
        long thinkMillis = Long.parseLong(prop("thinkMillis", "500"));

        System.out.printf("LoadRunner: baseUrl=%s vus=%d duration=%ds think=%dms%n",
                baseUrl, vus, durationSeconds, thinkMillis);

        Instant deadline = Instant.now().plusSeconds(durationSeconds);
        AtomicLong iterations = new AtomicLong();
        AtomicLong failures = new AtomicLong();
        ExecutorService pool = Executors.newFixedThreadPool(vus);

        for (int i = 0; i < vus; i++) {
            final int vu = i + 1;
            pool.submit(() -> virtualUser(vu, baseUrl, deadline, thinkMillis, iterations, failures));
        }

        pool.shutdown();
        if (!pool.awaitTermination(durationSeconds + 120L, TimeUnit.SECONDS)) {
            pool.shutdownNow();
        }

        System.out.printf("done: iterations=%d failures=%d%n", iterations.get(), failures.get());
        if (iterations.get() == 0) {
            System.exit(1);
        }
    }

    private static void virtualUser(int vu, String baseUrl, Instant deadline, long thinkMillis,
                                    AtomicLong iterations, AtomicLong failures) {
        WebDriver driver = null;
        try {
            driver = BrowserFactory.create();
            PortalScenario scenario = new PortalScenario(driver, baseUrl);
            while (Instant.now().isBefore(deadline)) {
                try {
                    scenario.runOnce();
                    iterations.incrementAndGet();
                } catch (RuntimeException e) {
                    failures.incrementAndGet();
                    System.err.printf("vu%d iteration failed: %s%n", vu, e.getMessage());
                }
                sleep(thinkMillis);
            }
        } catch (RuntimeException e) {
            System.err.printf("vu%d could not start: %s%n", vu, e);
        } finally {
            if (driver != null) {
                driver.quit();
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String prop(String key, String dflt) {
        String v = System.getProperty(key);
        return (v != null && !v.isBlank()) ? v : dflt;
    }

    private LoadRunner() {
    }
}
