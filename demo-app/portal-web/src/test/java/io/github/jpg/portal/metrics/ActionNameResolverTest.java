package io.github.jpg.portal.metrics;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ActionNameResolverTest {

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
    void mapsKnownComponentIds() {
        assertEquals("login",
                ActionNameResolver.resolve(Map.of("javax.faces.source", "loginForm:loginButton"), "/login.xhtml"));
        assertEquals("discharge.save",
                ActionNameResolver.resolve(Map.of("javax.faces.source", "dischargeForm:saveButton"),
                        "/secure/discharge.xhtml"));
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
