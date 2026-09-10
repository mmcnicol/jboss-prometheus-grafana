# Requirements — Load-Test Metrics for a JBoss/JSF Web Application

Status: **Draft for review**
Date: 2026-09-10

## 1. Background and context

A Jakarta EE 8 web application is hosted on JBoss EAP 7.4. The UI layer uses JSF
and PrimeFaces 13.0.0 with a commercial layout theme. The system comprises one
main application WAR and roughly 30 microservice WARs. Delivery uses Git
(`main`, `develop`, release branches) and Maven, with Jenkins for CI.

Environment split matters here: the **dev/CI hosts** (Jenkins and agents,
Selenium hub, Nexus, Docker registry) run **RHEL**, and Prometheus, Grafana, and
`node_exporter` already run there for host CPU/memory dashboards. **Test, UAT, and
Production run on Windows servers**, have **no** `node_exporter`, and are covered
by a separate application-management team's commercial APM product. The load test
that this work targets runs against a **Windows-hosted** deployment, so the
solution must not depend on Linux-only host tooling.

Earlier work (a spike) explored getting application metrics into Prometheus for
**load testing, not production monitoring**. That spike tried Micrometer and then
the Prometheus Java client, and expanded (arguably too far) into JVM/app-server
MBean metrics. None of it reached the production application. A further load test
is expected before a subsequent go-live.

This repository is a **generic, non-confidential reference implementation** of the
approach: JBoss (WildFly as the redistributable stand-in for EAP 7.4) +
Prometheus + Grafana, with a small JSF/PrimeFaces application and two
microservices standing in for the real system.

## 2. Goals

- **G1** — Capture timings for key user actions during a load test (e.g. from
  clicking *Login* to the next page being rendered), export them to Prometheus,
  and visualise them in Grafana.
- **G2** — Produce a Grafana **trend chart** that shows a baseline load-test run
  and lets a later release's run be overlaid / compared on the same chart.
- **G3** — Metrics collection is **opt-in per run**: the application does not
  continuously emit metrics. A JBoss system property enables/disables it, and
  collection happens only for the duration of a load test.
- **G4** — Keep the first increment **simple** and demonstrable end-to-end before
  adding breadth. (A prior attempt lost time by starting with JVM/app-server
  internals.)
- **G5** — Provide a second, separate dashboard for app-server/JVM metrics
  (JBoss, GC, threads, datasource pool) collected **only during a load test** —
  delivered as a later phase, not the first increment.
- **G6** — Everything reproducible by a single person on a modest machine or one
  cloud VM, with no dependency on confidential workplace infrastructure.
- **G7** — Result is portable to the real application with minimal change, and
  has a credible forward path to JBoss EAP 8.x / Jakarta EE 10 and, if the
  organisation chooses, to Spring Boot + Micrometer.

## 3. Non-goals

- Production monitoring, alerting, on-call, or SLOs.
- Replacing or evaluating the incumbent commercial APM product.
- Distributed tracing as a primary deliverable (a tracing hook may be noted for a
  later phase but is not required here).
- Redistributing any commercial artefact (EAP binaries, commercial PrimeFaces
  themes). These are referenced and substituted, never committed.
- Real patient data or any workplace-specific configuration, hostnames,
  credentials, or scheme/form content.

## 4. Stakeholders

| Role | Interest |
|---|---|
| Engineer (repo owner) | Build it, then propose it at work. |
| Tech lead | Wants it kept simple; interested in a Spring Boot + Micrometer direction; wants to showcase the result to the application-management team. |
| Application-management team | Own APM today; may change tools later; potential future consumer of the approach. |
| Performance/QA colleague | Owns existing Selenium load scripts. |

## 5. Functional requirements

### 5.1 Application instrumentation

- **FR1** — The demo application exposes application metrics in Prometheus format
  (via scrape endpoint and/or OTLP push) **only when enabled**.
- **FR2** — Enablement is controlled by a **JBoss/WildFly system property**
  (working name `portal.metrics.enabled`, default `false`). No code redeploy is
  required to switch a server between "load test" and "normal" modes — only a
  property change and restart (restart acceptable for load-test environments).
- **FR3** — When disabled, instrumentation adds negligible overhead: no metrics
  endpoint is served, no meter registry / collectors are registered, and
  request-path timing code is a no-op or absent from the hot path.
- **FR4** — The application records, at minimum, these **user-action timers**
  (server-side elapsed time, with count and a latency distribution / percentiles):
  - login submit → authenticated landing page served;
  - open the main clinical form (stand-in form) page;
  - save/submit the form;
  - a representative read/list page.
- **FR4a** — The action is identified **server-side** (from the JSF view id /
  navigation outcome and `javax.faces.source`), because the app uses a JSF page
  template and the URL barely changes between actions. A load driver must **not**
  be required to mark the request for the metric to be attributed correctly — so
  the same instrumentation works with a Selenium/WebDriver driver, a k6 browser
  driver, or a k6 HTTP driver.
- **FR4b** — PrimeFaces polling / auto-refresh requests are identified and
  excluded from user-action timers (or recorded under a separate `poll` action),
  so they neither inflate counts nor distort latency.
- **FR5** — Each user-action metric carries labels for at least: action name,
  outcome (success/failure), and HTTP status class.
- **FR6** — Microservice WARs expose the same style of instrumentation for a
  small number of representative endpoints (the "middle" load-test layer, where
  endpoints have real URLs and stable contracts).
- **FR7** — The instrumentation approach is implemented **twice** for comparison
  (Micrometer with a Prometheus registry; Prometheus Java client directly),
  behind the same toggle and the same internal timing API, so a recommendation
  can be made from evidence. Only one is carried forward as the default.

### 5.2 Collection and run labelling

- **FR8** — A collector process (OpenTelemetry Collector) scrapes / receives the
  application metrics **only for the duration of a load-test run**.
- **FR9** — Every metric sample from a run is tagged with run metadata:
  `run_id` (unique), `release` (version/branch/tag under test), `test_type`
  (e.g. `ui`, `service`, `mixed`), `env`, and `started_at`. This tagging is
  applied **outside** the application (in the collector / push pipeline) so the
  same application build can be used for many runs.
- **FR10** — Where the scenario driver emits its own client-side timings (k6
  HTTP or k6 browser), they are captured for the same run and tagged with the
  same `run_id` / `release` and the same action names, so client-observed and
  server-observed timings can be compared. Drivers that cannot emit metrics
  (plain Selenium) are still fully supported — the server-side timer is the
  source of truth.
- **FR11** — Collected run data is retained long enough to compare across
  releases (weeks–months); raw high-resolution data may be down-sampled or
  summarised after a run.

### 5.3 Visualisation

- **FR12** — A Grafana dashboard **"User Actions — Load Test"** showing, per
  action: request rate, error rate, and latency percentiles (p50/p90/p95/p99)
  over the run's elapsed time.
- **FR13** — A **baseline-vs-candidate overlay**: select two runs (e.g. by
  `release` or `run_id`) and see both on one time-series panel aligned on
  **elapsed time since run start** (not wall-clock), plus a summary table of
  percentile deltas.
- **FR14** — A **release trend panel**: one point (e.g. p95 login time) per run,
  x-axis = release / date, so regression or improvement across releases is
  visible at a glance.
- **FR15** — Dashboards are provisioned from version-controlled JSON (no manual
  clicking to reproduce). Prometheus and collector config are likewise in the
  repo.
- **FR16** — A later-phase dashboard **"App Server & JVM — Load Test"**: heap/GC,
  thread pools, datasource connection pool usage, request throughput at the
  container level — sourced only during a run.

### 5.4 Automation / CI

- **FR17** — A Jenkins pipeline definition that: starts collection, runs a load
  test with a supplied `release` + `run_id`, stops collection, and records the
  run. A GitHub Actions workflow builds and tests the repo (public-repo
  friendliness); Jenkins is the load-run orchestrator.
- **FR18** — The pipeline can attach a snapshot (image or Grafana snapshot link)
  of the key panel to the build result.
- **FR19** — A scenario is defined once as an ordered list of steps
  (login → open form → save form) and can be executed by a **pluggable driver**:
  Selenium (Java/TestNG), k6 browser, or k6 HTTP. All drivers run headless and
  loop the steps under concurrency. The UI driver drives the real JSF/PrimeFaces
  DOM (AJAX + template navigation); the service driver hits endpoint URLs
  directly. A recorded-HTTP script for the UI is kept only as a documented
  example of what not to rely on (large, brittle, polling noise).

### 5.5 Quality / project constraints

- **FR20** — Instrumentation code has unit tests and passes the project's static
  analysis. Classes that only read MBeans / container internals (hard to
  unit-test) are isolated behind an interface so the untestable surface is small
  and its coverage exclusion is explicit and justified.
- **FR21** — No secrets, workplace identifiers, or licensed binaries in the repo.
- **FR22** — Primary language is Java/Maven, matching workplace norms. Any
  helper tooling in another language is optional, isolated, and not required to
  run the core solution.

## 6. Non-functional requirements

- **NFR1 — Overhead**: with metrics enabled, added server-side latency per
  instrumented request < ~1 ms median and negligible allocation; with metrics
  disabled, no measurable difference from an uninstrumented build.
- **NFR2 — Isolation**: nothing in the metrics path can fail a user request
  (instrumentation failures are swallowed and counted, never propagated).
- **NFR3 — Footprint**: the whole stack (app server, 2 services, Prometheus,
  Grafana, OTel Collector, k6) runs on a single 4 vCPU / 16 GB VM.
- **NFR4 — Reproducibility**: `git clone` + a documented command (or two) brings
  the stack up; teardown is one command; cloud costs are near-zero when stopped.
- **NFR5 — Portability**: the instrumentation module is a self-contained Maven
  module with no dependency on the demo app, so it can be dropped into the real
  WARs.
- **NFR6 — Compatibility**: builds and runs on WildFly 26.1 (Jakarta EE 8,
  `javax.*`, MicroProfile 4.x); documented deltas for EAP 7.4 and a documented
  path to EAP 8 / EE 10 (`jakarta.*`).
- **NFR6a — OS-agnostic**: every part of the collection path (instrumentation
  module, metrics endpoint, OTel Collector, k6, and any phase-2 JBoss/JVM
  exporter) must run unchanged whether the app server is on RHEL or **Windows**,
  since Test/UAT/Production are Windows. No dependency on Linux-only exporters,
  shell scripts, or `node_exporter` for the target host.
- **NFR7 — Security**: the metrics endpoint is not exposed publicly; bound to a
  management interface / internal network / behind auth on the VM.

## 7. Assumptions

- A short JBoss/WildFly restart is acceptable to toggle metrics in load-test
  environments.
- Load-test runs are infrequent enough that per-run labels (`run_id`) do not
  create Prometheus cardinality problems; retention of summarised per-run data is
  the long-lived part.
- The GCP project `mmcnicol-geneology` (already configured in the local SDK) may
  be used for a temporary VM; it will be stopped/deleted between sessions.
- The engineer's machine is a live "Try Ubuntu" USB session: no local
  Docker/Podman, no local JDK/Maven yet, changes are lost on freeze unless
  committed and pushed. Frequent small commits are required.

## 8. Open questions

- **OQ1** — Preferred free PrimeFaces theme for the demo (the workplace uses a
  commercial layout theme that cannot be redistributed). Proposal: a built-in
  free PrimeFaces theme, with a documented "swap point" for the commercial one.
- **OQ2** — Is a real EAP 7.4 parity check on the VM wanted in this phase, or
  deferred? (Requires a Red Hat developer subscription that the engineer would
  supply on the VM.)
- **OQ3** — Target release/branch naming to use as the `release` label
  (`git describe`, branch name, or a supplied build number)?
- **OQ4** — Is a lightweight results store (like the engineer's earlier Go "test
  store") wanted as a durable home for per-run summaries, or is Prometheus +
  recording rules sufficient for the trend history?

## 9. Acceptance criteria (first increment)

1. `portal.metrics.enabled=false` build/run: no `/metrics`, no measurable
   overhead. `=true`: user-action timers visible in Prometheus.
2. Two scenario runs executed with different `release` labels; server-side
   per-action timers from both visible in Prometheus tagged by `run_id` /
   `release`; polling requests absent from the user-action timers.
2a. The same scenario run by two different drivers (e.g. Selenium and k6 browser)
   produces the same server-side action metrics.
3. Grafana "User Actions — Load Test" dashboard provisioned from repo JSON shows
   per-action percentiles for a run.
4. Overlay panel shows baseline vs candidate on elapsed-time axis + delta table.
5. Release trend panel shows one p95-login point per run across ≥ 3 runs.
6. Instrumentation module builds green with unit tests and static analysis;
   MBean-reading code (if any in this increment) isolated behind an interface.
7. One documented command brings the stack up on the VM; one tears it down.
8. `docs/` explains how to port the instrumentation module into the real WARs
   and the EAP 7.4 / EAP 8 deltas.
