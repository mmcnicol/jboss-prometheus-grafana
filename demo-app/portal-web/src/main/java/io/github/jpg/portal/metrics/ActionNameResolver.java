package io.github.jpg.portal.metrics;

import javax.faces.context.FacesContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Turns a JSF request into a stable, low-cardinality action name — or
 * {@code null} for a request that should not be timed (polling / auto-refresh).
 *
 * <p>Priority:
 * <ol>
 *   <li>polling component id &rarr; {@code null} (dropped)</li>
 *   <li>explicit {@code _action} request parameter, if a driver set one</li>
 *   <li>{@code javax.faces.source} — the firing component of an AJAX request</li>
 *   <li>a command-button client id present as a POST parameter (non-AJAX
 *       postback: {@code login}, {@code discharge.save} both submit and redirect)</li>
 *   <li>the view id (a plain GET of a page)</li>
 * </ol>
 *
 * <p>Phase 1 keeps the maps small and hard-coded. Spike B replaces them with the
 * real component-id map and polling-component list, and decides whether polling
 * is dropped or bucketed under a {@code poll} action.
 */
final class ActionNameResolver {

    /** {@code javax.faces.source} / button client-id suffixes and their action names. */
    private static final Map<String, String> COMPONENT_ACTIONS = new LinkedHashMap<>();

    static {
        COMPONENT_ACTIONS.put("loginButton", "login");
        COMPONENT_ACTIONS.put("saveButton", "discharge.save");
    }

    /** Client-id suffixes that identify a poll / auto-refresh request. */
    private static final Set<String> POLL_COMPONENT_IDS = Set.of("countPoll");

    private ActionNameResolver() {
    }

    static String resolve(FacesContext ctx) {
        Map<String, String> params = ctx.getExternalContext().getRequestParameterMap();
        String viewId = ctx.getViewRoot() != null ? ctx.getViewRoot().getViewId() : null;
        return resolve(params, viewId);
    }

    /** Testable core: no FacesContext. */
    static String resolve(Map<String, String> params, String viewId) {
        String source = params.get("javax.faces.source");
        if (source != null && POLL_COMPONENT_IDS.contains(tail(source))) {
            return null;
        }

        String explicit = params.get("_action");
        if (explicit != null && !explicit.isBlank()) {
            return sanitize(explicit);
        }

        if (source != null && !source.isBlank()) {
            String mapped = COMPONENT_ACTIONS.get(tail(source));
            if (mapped != null) {
                return mapped;
            }
        }

        // Non-AJAX postback: the clicked command button's client id is a param key.
        boolean postback = params.containsKey("javax.faces.ViewState");
        if (postback) {
            for (String key : params.keySet()) {
                String mapped = COMPONENT_ACTIONS.get(tail(key));
                if (mapped != null) {
                    return mapped;
                }
            }
        }

        return fromViewId(viewId);
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

    private static String tail(String clientId) {
        return clientId.substring(clientId.lastIndexOf(':') + 1);
    }

    private static String sanitize(String s) {
        String cleaned = s.trim().toLowerCase().replaceAll("[^a-z0-9._-]", "_");
        return cleaned.length() > 60 ? cleaned.substring(0, 60) : cleaned;
    }
}
