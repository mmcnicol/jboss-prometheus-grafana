# Spike B — Measuring a JSF/PrimeFaces user action

Status: **first pass complete** (Phase 1). A working measurement point and a
driver-independent action-naming scheme, verified with the Selenium driver.

## Measurement point

A `PhaseListener` (`ActionTimingPhaseListener`), `ANY_PHASE`:

- **start** = `beforePhase(RESTORE_VIEW)`
- **stop** = `afterPhase(RENDER_RESPONSE)` **or** `afterPhase(INVOKE_APPLICATION)`
  when `FacesContext.getResponseComplete()` is already true — this is the
  `faces-redirect` case (`login` and `discharge.save` both redirect, so
  RENDER_RESPONSE never runs for that request).
- recorded **once per request** (a flag in the request map).

Captures: server-side request handling (restore view → invoke → render). Does
**not** capture: client render, network, or user think time. A k6-browser
cross-check (Phase 2) will quantify the client-side gap.

## Action naming — no driver marking required

`ActionNameResolver`, in priority order:

1. `javax.faces.source` client-id suffix in `POLL_COMPONENT_IDS` → **drop**
   (return null, not timed).
2. explicit `_action` request param, if a driver chose to set one.
3. `javax.faces.source` suffix (AJAX requests) → mapped name.
4. non-AJAX **postback** (`javax.faces.ViewState` present) with a known command
   button's client id among the param **keys** → mapped name. *(A plain
   `h:commandButton` / `p:commandButton ajax="false"` posts its own client id as
   a parameter; `javax.faces.source` is AJAX-only.)*
5. view id → page-load action name (`login.view`, `discharge.view`,
   `discharge.list`).

Verified counts for a 71-iteration Selenium run:

| action | count | note |
|---|---|---|
| `login.view` | 55 | GET of the login page |
| `login` | 55 | the sign-in POST (redirect) |
| `discharge.view` | 55 | GET of the form |
| `discharge.save` | 55 | the save POST (redirect) |
| `discharge.list` | 126 | GET of the list — twice per iteration (after login, after save) |

p95: `login` 0.133 s (vs 120 ms simulated auth), `discharge.save` 0.040 s,
`discharge.list` 0.028 s — all consistent with the simulated delays.

## Polling filter

`p:poll id="countPoll" interval="5"` on the list page auto-refreshes the record
count. Its AJAX request carries `javax.faces.source=listForm:countPoll` and is
dropped by rule 1 — it never appears as an action and never inflates
`discharge.list`. Confirmed: no `poll` series, `discharge.list` count matches the
expected 2×iterations.

## Open items for a deeper pass

- The component-id map (`loginButton`, `saveButton`, `countPoll`) is hard-coded
  in `ActionNameResolver`. For the real app it should be externalised (a
  properties file or annotation) so teams add actions without touching the
  listener.
- Decide drop-vs-bucket for polling once there are several poll components
  (current choice: drop).
- `discharge.list` being counted twice per iteration is correct but worth a note
  on dashboards ("page loads, not user intents").
- AJAX partial-render actions (none in the demo yet) — add one and confirm the
  RENDER_RESPONSE stop still fires for a partial response.
