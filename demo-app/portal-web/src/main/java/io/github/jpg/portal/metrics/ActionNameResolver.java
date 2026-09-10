package io.github.jpg.portal.metrics;

import javax.faces.context.FacesContext;
import java.util.Map;

/**
 * Turns a JSF request into a stable, low-cardinality action name.
 *
 * <p>Priority: (1) an explicit {@code _action} request parameter if a driver set
 * one; (2) the {@code javax.faces.source} component id for AJAX/postback
 * requests, mapped to a friendly name; (3) the view id.
 *
 * <p>Phase 0 keeps the map tiny and hard-coded. Spike B produces the real map
 * and the polling-component filter.
 */
final class ActionNameResolver {

    private ActionNameResolver() {
    }

    static String resolve(FacesContext ctx) {
        Map<String, String> params = ctx.getExternalContext().getRequestParameterMap();
        String viewId = ctx.getViewRoot() != null ? ctx.getViewRoot().getViewId() : null;
        return resolve(params, viewId);
    }

    /** Testable core: no FacesContext. */
    static String resolve(Map<String, String> params, String viewId) {
        String explicit = params.get("_action");
        if (explicit != null && !explicit.isBlank()) {
            return sanitize(explicit);
        }

        String source = params.get("javax.faces.source");
        if (source != null && !source.isBlank()) {
            String mapped = fromComponentId(source);
            if (mapped != null) {
                return mapped;
            }
        }

        return fromViewId(viewId);
    }

    private static String fromComponentId(String clientId) {
        // clientId looks like "loginForm:loginButton"
        String tail = clientId.substring(clientId.lastIndexOf(':') + 1);
        switch (tail) {
            case "loginButton":
                return "login";
            case "saveButton":
                return "discharge.save";
            default:
                return null;
        }
    }

    private static String fromViewId(String viewId) {
        if (viewId == null) {
            return "unknown";
        }
        switch (viewId) {
            case "/login.xhtml":
                return "login.view";
            case "/secure/discharge.xhtml":
                return "discharge.view";
            case "/secure/discharges.xhtml":
                return "discharge.list";
            default:
                return sanitize(viewId.replaceAll("^/|\\.xhtml$", "").replace('/', '.'));
        }
    }

    private static String sanitize(String s) {
        String cleaned = s.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "_");
        return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
    }
}
