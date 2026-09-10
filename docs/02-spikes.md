# Spikes

Status: **Draft for review**
Date: 2026-09-10

Each spike is time-boxed, has a specific question, and produces a written
finding in `docs/findings/` plus throwaway or clearly-marked prototype code.
Spikes are ordered so the first few de-risk the first increment; later ones
inform phase 2.

---

## Spike A — Instrumentation library comparison (Micrometer vs Prometheus Java client)

**Question.** On WildFly 26.1 (EAP 7.4 stand-in), which library gives the
simpler, lower-risk way to record user-action timers behind a system-property
toggle — Micrometer with a Prometheus registry, or the Prometheus Java client
(`simpleclient` / the newer `client_java` 1.x)?

**Do.**
- Define one internal API (`ActionTimer` / `Metrics` facade) and implement it
  twice.
- Wire both to a single toggle (`portal.metrics.enabled`).
- Check for collisions with WildFly's bundled MicroProfile Metrics /
  SmallRye subsystem; decide whether to disable that subsystem.
- Measure: dependency footprint (transitive jars in the WAR), lines of glue
  code, cold-start cost, per-call overhead (JMH or a crude loop), behaviour when
  disabled.
- Assess testability: how easy is a unit test for "timer recorded on success,
  error counter on exception"? What must JaCoCo/Sonar exclude, if anything?

**Time-box.** 2 days.

**Output.** Recommendation with a table (footprint / overhead / testability /
Spring Boot alignment / EE 10 forward path). One library becomes the default;
the other stays behind the facade for a while as proof the abstraction holds.

**Decision it informs.** FR7, FR20, G7.

---

## Spike B — Measuring a JSF/PrimeFaces user action end-to-end

**Question.** What is the most reliable place to measure "user clicked *Login* →
next page rendered", and how do server-side and client-side measurements differ
for PrimeFaces AJAX interactions?

**Do.**
- Server-side: a servlet `Filter` around the FacesServlet vs a JSF
  `PhaseListener` (RESTORE_VIEW → RENDER_RESPONSE). Handle AJAX partial
  responses and redirects (POST-then-redirect-GET login flow).
- Client-side: k6 browser and/or a Navigation Timing capture; compare with the
  server number for the same action.
- Decide how an "action" is identified: a request header/param injected by the
  load script, a URL pattern, or a JSF component id.

**Time-box.** 2 days.

**Output.** A recommended measurement point for the demo, notes on what it does
and does not capture (client render, network, think time), and how the load
scripts should mark actions.

**Decision it informs.** FR4, FR5, FR10.

---

## Spike C — Export path and per-run labelling (OTel Collector vs Pushgateway vs k6 remote-write)

**Question.** How do we attach `run_id` / `release` / `test_type` to every
sample for a run, collect **only during the run**, and keep the door open for
adding JBoss/JVM metrics later — without rebuilding or reconfiguring the app per
run?

**Do.** Prototype and document the three candidates:

| Option | How labels are added | App change | Pros | Cons |
|---|---|---|---|---|
| **1. OTel Collector** scrapes app `/metrics`, adds `run_id`/`release` as resource/attributes via a per-run collector config or env, `prometheusremotewrite` to Prometheus | in collector | app exposes an endpoint (small) | run labels without app redeploy; same pipeline later carries JBoss/JVM + traces; collector lifecycle = run lifecycle | one more process to manage; learn collector config |
| **2. Prometheus Pushgateway** — app or a sidecar pushes on a schedule with a grouping key per run | in push grouping key | Prometheus-native | simple mental model | designed for batch jobs; wrong tool for long series; label/lifecycle awkwardness (this is why the earlier spike moved off it) |
| **3. k6 `--out experimental-prometheus-rw`** with `testid`/`release` tags | in k6 CLI tags | none | zero app change; client-side truth | only k6's view; cannot extend to server-internal / JBeans / JVM |

- For option 1, test two label mechanisms: collector `resource` processor with
  env vars set by the CI job, vs a templated collector config per run.
- Confirm "collect only during run": collector started by the CI job, stopped at
  the end; app endpoint present but unscraped otherwise.

**Time-box.** 2–3 days.

**Output.** A recommendation (expected: **Collector as the spine, k6 remote-write
alongside for client-side truth, Pushgateway rejected**) and a written
comparison for the showcase. Note the trade-off explicitly: option 3 needs no
app code but cannot grow to server-internal metrics, which is why an app-side
endpoint plus a collector is preferred.

**Decision it informs.** FR1, FR8, FR9, FR10, G3, G5.

---

## Spike D — Grafana baseline-vs-candidate overlay and release trend

**Question.** How do we show two runs on one panel aligned on elapsed time, and
one-point-per-run across releases, from Prometheus data — reliably and
reproducibly from checked-in JSON?

**Do.**
- Overlay: compare approaches —
  (a) Grafana per-query **time shift** to slide an older run onto a newer one;
  (b) record an **elapsed-seconds** label/metric at collection time and plot
  against that;
  (c) two dashboard **template variables** (`$baseline`, `$candidate`) over the
  `run_id`/`release` label with `{run_id=~"$baseline|$candidate"}`.
- Trend: `max_over_time(login_seconds{quantile="0.95"}[$run_window])` per run,
  or a Prometheus **recording rule** that writes one summary series per run;
  x-axis = release. Consider a small results store (OQ4) if recording rules get
  awkward.
- Delta table: `p95(candidate) - p95(baseline)` per action.

**Time-box.** 2–3 days.

**Output.** Working dashboard JSON for both panels + a note on which overlay
technique to standardise on and why. This is the panel the tech lead wants for
the showcase — treat it as the primary deliverable.

**Decision it informs.** FR12, FR13, FR14, G2.

---

## Spike E — App-server / JVM metrics without a testability mess (phase 2)

**Question.** For JBoss/WildFly + JVM metrics collected only during a run, what
is the least-effort, least-untestable-code route: MicroProfile Metrics subsystem,
Micrometer's JVM/JMX binders, the standalone `jmx_exporter` agent, or the
Collector's JMX receiver?

**Do.**
- Compare each for: coverage (heap/GC/threads/datasource pool/undertow),
  amount of custom Java (ideally zero), how it's toggled per run, and how it
  rides the same Collector pipeline from Spike C.
- If custom MBean-reading Java is unavoidable, define the interface boundary and
  the exact JaCoCo/Sonar exclusion, with justification.

**Time-box.** 2 days.

**Output.** Recommendation (expected: **an agent/receiver approach with no custom
Java** — `jmx_exporter` agent or Collector JMX receiver — so nothing needs unit
tests). Feeds the "App Server & JVM" dashboard.

**Decision it informs.** FR16, FR20, G5.

---

## Spike F — CI orchestration of a labelled run

**Question.** What does the Jenkins pipeline look like that runs a load test,
tags it, waits for metrics to land, snapshots the panel, and attaches it to the
build — and how much can be shared with GitHub Actions?

**Do.**
- Pipeline stages: resolve `release`/`run_id` → start collector with run env →
  k6 run → drain/settle → stop collector → Grafana snapshot API → archive
  artifact.
- Decide `run_id` scheme (`${JOB_NAME}-${BUILD_NUMBER}` + timestamp) and
  `release` scheme (`git describe --tags --always` or branch).
- GitHub Actions: build + unit tests + dashboard JSON lint only (no load run).

**Time-box.** 1–2 days.

**Output.** `Jenkinsfile` + a documented manual fallback. 

**Decision it informs.** FR17, FR18, OQ3.

---

## Spike G — EAP parity and forward path (optional, low priority)

**Question.** Where might WildFly 26.1 and EAP 7.4 diverge for this solution, and
what breaks on EAP 8 / Jakarta EE 10 (`jakarta.*` namespace)?

**Do.** Desk review + optionally deploy the same WARs to an EAP 7.4 trial on the
VM. List: subsystem defaults (MicroProfile Metrics on/off), module/classloading
differences, `javax.*`→`jakarta.*` for the instrumentation module, Micrometer /
Prometheus client versions that support both.

**Time-box.** 1 day (desk) + 0.5 day (VM, if done).

**Output.** A "porting notes" section in `docs/`.

**Decision it informs.** NFR6, G7, OQ2.

---

## Suggested sequencing

1. **A**, **B**, **C** in parallel-ish (they touch different layers) — these
   unblock the first increment.
2. **D** as soon as C produces labelled data.
3. **F** to make runs repeatable.
4. **E**, **G** for phase 2 / the showcase.

Total: ~2 weeks of focused effort for A–D+F; E/G follow.
