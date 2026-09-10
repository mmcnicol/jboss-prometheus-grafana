package io.github.jpg.portal.metrics;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ActionNameResolverTest {

    private static Map<String, String> params(String... kv) {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    void pollRequestsAreDropped() {
        assertNull(ActionNameResolver.resolve(
                Map.of("javax.faces.source", "listForm:countPoll",
                        "javax.faces.partial.ajax", "true"),
                "/secure/discharges.xhtml"));
    }

    @Test
    void pollDropWinsOverExplicitAction() {
        assertNull(ActionNameResolver.resolve(
                Map.of("javax.faces.source", "listForm:countPoll", "_action", "sneaky"),
                "/secure/discharges.xhtml"));
    }

    @Test
    void explicitActionParamWins() {
        assertEquals("custom.step",
                ActionNameResolver.resolve(Map.of("_action", "custom.step",
                        "javax.faces.source", "loginForm:loginButton"), "/login.xhtml"));
    }

    @Test
    void mapsKnownAjaxSourceComponents() {
        assertEquals("login",
                ActionNameResolver.resolve(Map.of("javax.faces.source", "loginForm:loginButton"), "/login.xhtml"));
        assertEquals("discharge.save",
                ActionNameResolver.resolve(Map.of("javax.faces.source", "dischargeForm:saveButton"),
                        "/secure/discharge.xhtml"));
    }

    @Test
    void mapsNonAjaxPostbackFromButtonParamKey() {
        assertEquals("login", ActionNameResolver.resolve(
                params("javax.faces.ViewState", "-123", "loginForm", "loginForm",
                        "loginForm:loginButton", "Sign in"),
                "/login.xhtml"));
        assertEquals("discharge.save", ActionNameResolver.resolve(
                params("javax.faces.ViewState", "-123", "dischargeForm:saveButton", "Save discharge"),
                "/secure/discharge.xhtml"));
    }

    @Test
    void plainGetIsNotTreatedAsPostback() {
        // no ViewState -> a button-shaped key would still not trigger postback logic
        assertEquals("login.view", ActionNameResolver.resolve(
                params("loginForm:loginButton", "Sign in"), "/login.xhtml"));
    }

    @Test
    void fallsBackToViewIdForUnknownComponent() {
        assertEquals("discharge.list",
                ActionNameResolver.resolve(Map.of("javax.faces.source", "listForm:table"),
                        "/secure/discharges.xhtml"));
    }

    @Test
    void fallsBackToViewIdForPlainGet() {
        assertEquals("login.view", ActionNameResolver.resolve(Map.of(), "/login.xhtml"));
        assertEquals("discharge.view", ActionNameResolver.resolve(Map.of(), "/secure/discharge.xhtml"));
    }

    @Test
    void unknownViewIsSanitised() {
        assertEquals("secure.reports", ActionNameResolver.resolve(Map.of(), "/secure/reports.xhtml"));
    }

    @Test
    void nullViewIsUnknown() {
        assertEquals("unknown", ActionNameResolver.resolve(Map.of(), null));
    }
}
