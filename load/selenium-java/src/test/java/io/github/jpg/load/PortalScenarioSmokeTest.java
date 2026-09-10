package io.github.jpg.load;

import org.openqa.selenium.WebDriver;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Single-pass functional check of the scenario. Skipped unless {@code -DbaseUrl}
 * is supplied (it needs a running portal-web and a headless browser), so a plain
 * {@code mvn test} in CI stays fast and hermetic.
 *
 * <pre>
 *   mvn -pl load/selenium-java test -DbaseUrl=http://localhost:8080/portal-web
 * </pre>
 */
public class PortalScenarioSmokeTest {

    private WebDriver driver;
    private String baseUrl;

    @BeforeClass
    void setUp() {
        baseUrl = System.getProperty("baseUrl");
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new SkipException("set -DbaseUrl to run the Selenium smoke test");
        }
        driver = BrowserFactory.create();
    }

    @Test
    void completesHappyPath() {
        new PortalScenario(driver, baseUrl).runOnce();
    }

    @AfterClass(alwaysRun = true)
    void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
}
