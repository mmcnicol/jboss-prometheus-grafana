package io.github.jpg.portal.metrics;

import io.github.jpg.metrics.ActionTimer;
import io.github.jpg.metrics.Metrics;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.event.PhaseEvent;
import javax.faces.event.PhaseId;
import javax.faces.event.PhaseListener;
import java.time.Duration;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Times each JSF request from the start of RESTORE_VIEW to the end of
 * RENDER_RESPONSE and records it against a resolved action name.
 *
 * <p>Phase 0: a first cut. The action name is resolved from the JSF view id and,
 * for AJAX/postback requests, the {@code javax.faces.source} component id — the
 * load driver does not have to mark anything. Spike B refines the exact
 * measurement point, the component-id &rarr; name map, and the polling filter.
 *
 * <p>Never throws into the request: any failure here is logged and swallowed
 * (requirement NFR2).
 */
public class ActionTimingPhaseListener implements PhaseListener {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ActionTimingPhaseListener.class.getName());
    private static final String START_NANOS = ActionTimingPhaseListener.class.getName() + ".start";

    @Override
    public PhaseId getPhaseId() {
        return PhaseId.ANY_PHASE;
    }

    @Override
    public void beforePhase(PhaseEvent event) {
        if (event.getPhaseId() == PhaseId.RESTORE_VIEW) {
            requestMap(event).put(START_NANOS, System.nanoTime());
        }
    }

    @Override
    public void afterPhase(PhaseEvent event) {
        if (event.getPhaseId() != PhaseId.RENDER_RESPONSE) {
            return;
        }
        try {
            Object start = requestMap(event).get(START_NANOS);
            if (!(start instanceof Long)) {
                return;
            }
            Duration elapsed = Duration.ofNanos(System.nanoTime() - (Long) start);
            FacesContext ctx = event.getFacesContext();
            String action = ActionNameResolver.resolve(ctx);
            String outcome = ctx.isValidationFailed() || ctx.getMessageList().stream()
                    .anyMatch(m -> m.getSeverity().getOrdinal() >= javax.faces.application.FacesMessage.SEVERITY_ERROR.getOrdinal())
                    ? ActionTimer.FAILURE : ActionTimer.SUCCESS;

            Metrics.get().action(action).record(elapsed, outcome);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "action timing skipped", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requestMap(PhaseEvent event) {
        ExternalContext ext = event.getFacesContext().getExternalContext();
        return (Map<String, Object>) ext.getRequestMap();
    }
}
