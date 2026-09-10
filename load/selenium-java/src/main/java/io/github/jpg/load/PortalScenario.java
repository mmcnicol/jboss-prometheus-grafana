package io.github.jpg.load;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;
import java.util.UUID;

/**
 * The reusable scenario: sign in, open the discharge form, fill and save it.
 * Mirrors {@code load/scenarios/happy-path.md}.
 *
 * <p>Each step drives the real JSF/PrimeFaces DOM. Server-side instrumentation
 * records the per-action timing; this class only performs the clicks.
 */
public final class PortalScenario {

    private final WebDriver driver;
    private final String baseUrl;
    private final WebDriverWait wait;

    public PortalScenario(WebDriver driver, String baseUrl) {
        this.driver = driver;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(20));
    }

    /** One full pass through the scenario. */
    public void runOnce() {
        login("perf-user", "test");
        openNewDischargeForm();
        saveDischarge();
    }

    public void login(String username, String password) {
        driver.get(baseUrl + "/login.xhtml");
        driver.findElement(By.id("loginForm:username")).sendKeys(username);
        driver.findElement(By.id("loginForm:password")).sendKeys(password);
        driver.findElement(By.id("loginForm:loginButton")).click();
        wait.until(ExpectedConditions.urlContains("/secure/discharges.xhtml"));
    }

    public void openNewDischargeForm() {
        driver.get(baseUrl + "/secure/discharge.xhtml");
        wait.until(ExpectedConditions.presenceOfElementLocated(By.id("dischargeForm:patientReference")));
    }

    public void saveDischarge() {
        String ref = "PT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        driver.findElement(By.id("dischargeForm:patientReference")).sendKeys(ref);
        driver.findElement(By.id("dischargeForm:ward")).sendKeys("Ward A");
        driver.findElement(By.id("dischargeForm:summary")).sendKeys("Load-test generated discharge.");
        driver.findElement(By.id("dischargeForm:saveButton")).click();
        wait.until(ExpectedConditions.urlContains("/secure/discharges.xhtml"));
    }
}
