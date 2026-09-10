package io.github.jpg.load;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.time.Duration;

/**
 * Builds headless Chrome/Chromium WebDrivers.
 *
 * <p>Honours (env var / system property, system property wins):
 * <ul>
 *   <li>{@code CHROME_BIN} — browser binary (default {@code /usr/bin/chromium})</li>
 *   <li>{@code CHROMEDRIVER_BIN} — driver binary (default {@code /usr/bin/chromedriver})</li>
 * </ul>
 * Set explicitly so nothing tries to reach the network on a locked-down VM.
 */
final class BrowserFactory {

    private static final String CHROME_BIN = conf("CHROME_BIN", "/usr/bin/chromium");
    private static final String CHROMEDRIVER_BIN = conf("CHROMEDRIVER_BIN", "/usr/bin/chromedriver");

    private BrowserFactory() {
    }

    static WebDriver create() {
        File driver = new File(CHROMEDRIVER_BIN);
        if (driver.canExecute()) {
            System.setProperty(ChromeDriverService.CHROME_DRIVER_EXE_PROPERTY, CHROMEDRIVER_BIN);
        }

        ChromeOptions options = new ChromeOptions();
        File bin = new File(CHROME_BIN);
        if (bin.canExecute()) {
            options.setBinary(bin);
        }
        options.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1280,900");

        ChromeDriver wd = new ChromeDriver(options);
        wd.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
        wd.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
        return wd;
    }

    private static String conf(String name, String dflt) {
        String sys = System.getProperty(name);
        if (sys != null && !sys.isBlank()) {
            return sys;
        }
        String env = System.getenv(name);
        return (env != null && !env.isBlank()) ? env : dflt;
    }
}
