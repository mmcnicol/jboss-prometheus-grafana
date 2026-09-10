package io.github.jpg.portal.metrics;

import io.github.jpg.metrics.ActionTimer;
import io.github.jpg.metrics.Metrics;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;
import javax.faces.event.PhaseEvent;
import javax.faces.event.PhaseId;
import javax.faces.event.PhaseListener;
import java.time.Duration;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Times each JSF request against a resolved action name.
 *
 * <p>Window: start of RESTORE_VIEW to whichever comes first —
 * <ul>
 *   <li>the end of RENDER_RESPONSE (normal request), or</li>
 *   <li>the end of INVOKE_APPLICATION when the response is already complete
 *       (the {@code faces-redirect} case: login and save both redirect, so
 *       RENDER_RESPONSE never runs for that request).</li>
 * </ul>
 * Recorded once per request.
 *
 * <p>Polling requests (PrimeFaces {@code p:poll}) are dropped — see
 * {@link ActionNameResolver}.
 *
 * <p>Never throws into the request (requirement NFR2). Phase 1 first cut; Spike B
 * confirms the exact window and the component-id map.
 */
public class ActionTimingPhaseListener implements PhaseListener {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ActionTimingPhaseListener.class.getName());
    private static final String START_NANOS = ActionTimingPhaseListener.class.getName() + ".start";
    private static final String RECORDED = ActionTimingPhaseListener.class.getName() + ".done";

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
        PhaseId phase = event.getPhaseId();
        FacesContext ctx = event.getFacesContext();

        boolean endOfRender = phase == PhaseId.RENDER_RESPONSE;
        boolean redirectedAway = phase == PhaseId.INVOKE_APPLICATION && ctx.getResponseComplete();
        if (!endOfRender && !redirectedAway) {
            return;
        }

        try {
            Map<String, Object> requestMap = requestMap(event);
            if (requestMap.putIfAbsent(RECORDED, Boolean.TRUE) != null) {
                return; // already recorded for this request
            }
            Object start = requestMap.get(START_NANOS);
            if (!(start instanceof Long)) {
                return;
            }

            String action = ActionNameResolver.resolve(ctx);
            if (action == null) {
                return; // polling / ignored request
            }

            Duration elapsed = Duration.ofNanos(System.nanoTime() - (Long) start);
            Metrics.get().action(action).record(elapsed, outcome(ctx));
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "action timing skipped", e);
        }
    }

    private static String outcome(FacesContext ctx) {
        if (ctx.isValidationFailed()) {
            return ActionTimer.FAILURE;
        }
        boolean hasError = ctx.getMessageList().stream()
                .anyMatch(m -> m.getSeverity().getOrdinal() >= FacesMessage.SEVERITY_ERROR.getOrdinal());
        return hasError ? ActionTimer.FAILURE : ActionTimer.SUCCESS;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requestMap(PhaseEvent event) {
        return (Map<String, Object>) event.getFacesContext().getExternalContext().getRequestMap();
    }
}
