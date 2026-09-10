# Recommended Technical Solution

Status: **Approved 2026-09-10** — decisions locked in §9; implementation starts at Phase 0.
Date: 2026-09-10

---

## 1. One-paragraph summary

A self-contained Maven **instrumentation module** provides a tiny internal timing
API and two interchangeable backends (Micrometer and the Prometheus Java client),
both gated by the JBoss/WildFly system property `portal.metrics.enabled`
(default `false`). A demo **JSF/PrimeFaces WAR** plus **two microservice WARs**
use that module to time key user actions and endpoints. **The authoritative
timing for a UI action is measured server-side by the module** (a JSF
PhaseListener/filter), keyed by an action name — so the load driver only has to
*perform the scenario steps*, it does not have to measure a UI action in
isolation. During a load test, an **OpenTelemetry Collector** — started and
stopped by the CI job, so it lives exactly as long as the run — scrapes the app's
Prometheus endpoint, stamps every sample with `run_id` / `release` / `test_type`
/ `env`, and remote-writes to **Prometheus**. The scenario is driven by a
**pluggable driver** (k6 browser, Selenium/Java, or k6 HTTP for services); the
driver may also remote-write its own client-side timings with the same run tags
as a cross-check. **Grafana** dashboards, provisioned from checked-in JSON,
show per-action percentiles, a **baseline-vs-candidate overlay** on an
elapsed-time axis, and a **release trend** panel. Everything runs on one GCP VM
brought up and torn down by scripts in the repo.

---

## 2. Why this shape

| Requirement pressure | Design response |
|---|---|
| "Keep it simple first" (G4) | First increment is only: module + 4 user-action timers in the main WAR + one UI driver + Prometheus + one Grafana dashboard. Services, JVM metrics, trend panel come after. |
| Can't time a UI action "in isolation" with a load tool; JSF template = URL barely changes; recorded HTTP is huge/brittle; polling adds noise | **Measure server-side, keyed off JSF** (`javax.faces.source` / view id), not off the driver. Driver just performs steps. Poll requests filtered by component id. Driver is pluggable (Selenium/Java, k6 browser, k6 HTTP). |
| Opt-in per run, no continuous emission (G3) | System-property toggle in the app; **collector lifecycle = run lifecycle** (CI starts/stops it). App endpoint exists but is unscraped outside a run. |
| Run labels without redeploying the app (FR9) | Labels applied in the **collector**, from env vars the CI job sets per run — the same app build serves every run. |
| Room to add JBoss/JVM metrics later (G5) | Collector is the spine; phase 2 adds a `jmx_exporter` agent / Collector JMX receiver as another source into the same pipeline — **no new app Java**. |
| Decide Micrometer vs Prometheus client on evidence (FR7) | Both implemented behind one facade; Spike A picks the default; the loser stays wired for a while to prove the abstraction. |
| Portable to the real WARs (NFR5) | Instrumentation is its own module with no demo-app dependencies. |
| Public repo, no licensed binaries (FR21) | WildFly 26.1 not EAP; a free PrimeFaces theme with a documented swap point; no EAP/theme binaries committed. |
| Showcase value | The overlay + trend dashboard and the "two libraries compared" write-up are the artefacts the tech lead can show the app-management team. |

---

## 3. Architecture

```
                          ┌────────────────────────── GCP VM (e2-standard-4) ───────────────────────────┐
                          │                                                                            │
  Scenario driver         │   WildFly 26.1 standalone                                                   │
  - Selenium/Java (UI)    │     ├── portal-web.war   (JSF/PrimeFaces)  ─ server-side per-action timer    │
  - k6 browser (UI alt)   │─▶   ├── service-a.war    (JAX-RS / MicroProfile)                             │
  - k6 HTTP  (services)   │     └── service-b.war    (JAX-RS / MicroProfile)                             │
  run by CI               │            │  /metrics  (served only when portal.metrics.enabled=true)      │
     │                    │            ▼                                                                │
     │ k6 remote_write    │   OpenTelemetry Collector  ─ scrape ─▶ add run_id/release/test_type/env      │
     │ (run tags)         │      (started & stopped by the CI job)          │ prometheusremotewrite      │
     ▼                    │                                                ▼                            │
  Prometheus  ◀───────────┼──────────────────────────────────────────  Prometheus (TSDB + rules)        │
                          │                                                │                            │
                          │                                                ▼                            │
                          │                                          Grafana (provisioned JSON)         │
                          │                                                                            │
                          │   [phase 2] jmx_exporter -javaagent on WildFly ─▶ Collector ─▶ Prometheus    │
                          └────────────────────────────────────────────────────────────────────────────┘
```

- **node_exporter** is *not* part of this solution. In the real world it runs
  only on the **RHEL dev/CI hosts** (Jenkins, Selenium hub, Nexus, Docker
  registry) for host CPU/memory dashboards — **not** on Test/UAT/Production. Those
  environments are **Windows servers**; the load test that matters runs against a
  Windows-hosted app. Implications:
  - Host-level metrics for a load-test target are **out of scope** here. If ever
    wanted for a Windows target, that is `windows_exporter`, not `node_exporter` —
    noted, not built.
  - The instrumentation module and the k6/collector pipeline are **OS-agnostic**
    (pure JVM + HTTP), so they behave the same whether the app server is RHEL or
    Windows.
  - The **phase-2 JBoss/JVM route must be cross-platform**: a `-javaagent`
    (`jmx_exporter`) or the Collector's JMX receiver both work on Windows;
    anything relying on a Linux-only exporter or shell tooling does not. This
    reinforces the "agent, no custom Java" choice from Spike E.
  - On the demo **VM** (Debian) an optional `node_exporter` container may be added
    purely to make the compose stack feel complete for the showcase; it has no
    bearing on the real deployment.
- The observability stack (Prometheus, Grafana, Collector) runs via
  **docker compose on the VM** (the VM can run Docker; your laptop cannot).
  WildFly runs natively on the VM (simpler to attach a JMX agent later, closer to
  the real deployment).

---

## 4. Components in this repo

```
jboss-prometheus-grafana/
├── docs/
│   ├── 01-requirements.md
│   ├── 02-spikes.md
│   ├── 03-technical-solution.md
│   ├── findings/                 # spike write-ups land here
│   └── porting-notes.md          # how to drop the module into the real WARs; EAP deltas
├── metrics-support/              # THE portable module (Maven, no demo-app deps)
│   ├── metrics-api/              # ActionTimer / Metrics facade + Toggle + no-op impl
│   ├── metrics-micrometer/       # backend 1
│   ├── metrics-prometheus/       # backend 2 (Prometheus client_java 1.x)
│   └── metrics-jsf/              # PhaseListener/Filter that times JSF actions (servlet-api scope: provided)
├── demo-app/
│   ├── portal-web/               # JSF + PrimeFaces WAR: login, clinical-form stand-in, list page
│   ├── service-a/               # JAX-RS WAR
│   └── service-b/               # JAX-RS WAR
├── load/
│   ├── scenarios/                # step lists (login → open-form → save-form), driver-agnostic
│   ├── selenium-java/            # UI driver — Java + TestNG, Maven module (primary UI)
│   ├── k6/browser/               # UI driver — k6 browser (alternative UI + client cross-check)
│   ├── k6/http-services/         # service ("middle") endpoint load — hand-written
│   ├── k6/http-ui-recorded/      # kept only to demonstrate why recorded JSF HTTP is brittle
│   └── lib/                      # shared run-tagging helpers (run_id/release/test_type)
├── observability/
│   ├── docker-compose.yml        # prometheus + grafana + otel-collector
│   ├── prometheus/               # prometheus.yml, recording rules
│   ├── otel-collector/           # base config + per-run overlay template
│   └── grafana/provisioning/     # datasources + dashboards (JSON)
├── infra/gcp/
│   ├── up.sh / down.sh           # create/start & stop/delete the VM
│   ├── bootstrap.sh              # runs on the VM: install docker, java, k6, deploy WARs
│   └── README.md
├── ci/
│   ├── Jenkinsfile               # orchestrates a labelled load run
│   └── github-actions/build.yml  # build + unit tests + dashboard-lint
└── run.sh                        # convenience: local-ish end-to-end against the VM
```

---

## 5. Key design details

### 5.1 The toggle

- Read once at startup: `Boolean.getBoolean("portal.metrics.enabled")`.
- A single `Metrics` facade. When disabled it returns a shared no-op
  `ActionTimer`; nothing is registered; the `/metrics` servlet/endpoint is not
  mapped (a `ServletContainerInitializer` or MP Config check decides).
- WildFly: set via `bin/standalone.conf` `JAVA_OPTS` or
  `<system-properties>` in `standalone.xml`. EAP 7.4 identical.
- No feature flag framework; this is deliberately a JVM property so it matches
  how the real environments are configured and needs no infra.

### 5.2 The internal API (stable; backends swap under it)

```java
public interface ActionTimer { void record(Duration d, String outcome); }

public interface Metrics {
    ActionTimer action(String name);          // e.g. "login", "form.save"
    void increment(String counter, String... tags);
    static Metrics get() { /* returns NoOp or the configured backend */ }
}
```

Backends: `metrics-micrometer` binds a `PrometheusMeterRegistry`;
`metrics-prometheus` uses `client_java` 1.x histograms. Both expose the same
OpenMetrics text on the same path so the collector config is backend-agnostic.

### 5.3 Timing JSF user actions (`metrics-jsf`)

The real app uses a JSF page template, so **the URL barely changes as the user
navigates** — you cannot identify an action from the request path, and a
recorded HTTP script is large and brittle (lots of resource requests, plus
PrimeFaces polling). So the module identifies the action **from JSF itself**, not
from the driver:

- A `PhaseListener` records wall time from `RESTORE_VIEW` start to
  `RENDER_RESPONSE` end.
- **Action name resolution (in priority order):**
  1. the JSF navigation outcome / target view id for full-page navigations;
  2. `javax.faces.source` (the client id of the component that fired the AJAX
     request) mapped to a friendly name via a small config map — this covers
     "click Login", "click Save" without the driver doing anything;
  3. an explicit `_action` request parameter/header **if** a driver chooses to
     set one (k6 HTTP and k6 browser can; a plain Selenium/WebDriver script
     cannot easily — hence inference is the default, not the fallback).
- **Polling is filtered out**: requests whose `javax.faces.source` matches a
  configured set of `p:poll` / auto-refresh component ids are either ignored or
  recorded under a separate `poll` action, never mixed into user-action timers.
- Login POST→redirect→GET is correlated on a short-lived token.
- Outcome = `success` unless an exception is queued or HTTP status ≥ 400.
- Exact measurement point + the source-id map + the poll filter list are pinned
  down in **Spike B**.

Because the metric is produced server-side and keyed this way, **any driver that
performs the steps gets correct metrics** — the concern about using a load tool
to time a UI action "in isolation" goes away, and every step (login → open form →
save form) is recorded, with filtering done later in Grafana.

### 5.4 Collect-only-during-a-run

- The OTel Collector container is **not** in the always-on compose stack; it's
  started by `ci/Jenkinsfile` (or `run.sh`) with an env file:
  `RUN_ID`, `RELEASE`, `TEST_TYPE`, `ENV`, `SCRAPE_TARGET`.
- Collector pipeline: `prometheus` receiver (scrape app) → `resource` processor
  (add the run attributes) → `prometheusremotewrite` exporter → Prometheus.
- At run end the CI job stops the collector; scraping stops; the app keeps
  serving `/metrics` to no one (cheap) or you also flip the property back.

### 5.5 Scenario drivers (pluggable)

A scenario is an ordered list of steps (e.g. `login`, `open-form`, `save-form`)
run in a loop under concurrency. The **driver** is swappable; the server-side
metric is the source of truth in every case. Repo ships:

| Layer | Driver | Why | Notes |
|---|---|---|---|
| **UI ("top")** | **Selenium (Java + TestNG)** — primary | Java matches workplace norms; step-based and maintainable; drives the real JSF/PrimeFaces DOM incl. AJAX and template navigation | cannot easily set a request header → relies on server-side action **inference** (5.3) |
| **UI ("top")** | **k6 browser** — alternative | arguably easier to maintain than recorded HTTP; can set headers/tags | heavier, flakier; good for a quick client-side cross-check |
| **UI ("top")** | **k6 HTTP (recorded)** — documented, not recommended | works, but a JSF template app produces a large, brittle script full of resource + poll requests | keep only as a "here's why we don't" example |
| **Service ("middle")** | **k6 HTTP (hand-written)** — primary | microservice endpoints have real URLs and stable contracts; k6's own metrics are appropriate here | this is where `-o experimental-prometheus-rw` + `--tag` shines |

- Every driver run is tagged `testid=$RUN_ID`, `release=$RELEASE`,
  `test_type=$TEST_TYPE`.
- Where a driver emits its own timings (k6), they are grouped/labelled with the
  same action names as the server-side timers so client vs server latency lines
  up in Grafana.
- A thin `load/scenarios/<name>.md` defines each scenario's steps once;
  driver implementations follow it. Adding a Selenium-CLI or chromedp driver
  later is possible but not shipped — they would measure the same server-side
  metrics.
- The old problem ("I want a Jenkins job that hits one feature in a loop and only
  records a metric for one step") is solved by instrumentation: all steps are
  recorded when enabled; the loop just drives; Grafana filters to the step of
  interest.

### 5.6 Grafana

- **Dashboard 1 — "User Actions — Load Test"**: per-action rate / error% /
  p50-p90-p95-p99 over elapsed run time. Template variable `$run` over the
  `run_id` label.
- **Dashboard 2 — "Baseline vs Candidate"**: variables `$baseline` and
  `$candidate`; time-series panel with both series; overlay technique chosen in
  **Spike D** (leading candidate: record an `elapsed_seconds` axis at collection
  time so both runs start at 0; fallback: Grafana per-query time-shift). Plus a
  table of p95 deltas per action.
- **Dashboard 3 — "Release Trend"**: one point per run (p95 login, p95 form-save,
  error rate), x-axis = `release` / run start time. Backed by a Prometheus
  **recording rule** that writes a single summary series per run so the trend
  query stays trivial and history is compact.
- **Dashboard 4 — "App Server & JVM — Load Test"** (phase 2): heap, GC pause,
  threads, Undertow request count, datasource pool active/idle/wait.
- All JSON in `observability/grafana/provisioning/dashboards/`.

### 5.7 GCP VM

- `infra/gcp/up.sh`: `gcloud compute instances create` — `e2-standard-4`,
  Debian 12, 30 GB disk, a firewall rule limited to **your current public IP**
  for Grafana (3000) and SSH; everything else stays on `localhost`/VPC.
- `bootstrap.sh` on the VM: install Docker + Compose plugin, **JDK 17**, Maven,
  k6, a headless Chrome + matching Chromedriver for the Selenium and k6-browser
  drivers, download WildFly 26.1, deploy the three WARs, `docker compose up -d`
  the observability stack.
- `infra/gcp/down.sh`: delete the instance and firewall rule. Stopped/deleted
  between sessions → ~zero cost. Estimate: ~US$0.13/hr while running.
- Project `mmcnicol-geneology` (already in your gcloud config) unless you say
  otherwise.

### 5.8 CI

- **Jenkinsfile** stages: `Checkout` → `Build WARs` → `Deploy to WildFly`
  (or assume already deployed) → `Start collector (run env)` →
  `Run scenario (driver = param, loops the steps)` → `Settle 30s` →
  `Stop collector` → `Write recording rule eval` → `Grafana snapshot` →
  `Archive PNG/snapshot URL`.
- Parameters: `RELEASE` (default: `git describe --tags --always` **normalised**
  to a bare version — strip a leading `v`, a `version ` prefix, and any trailing
  `-<n>-g<sha>` from `git describe`; e.g. `version 1.0.0` → `1.0.0`), `TEST_TYPE`,
  `DURATION`, `VUS`, `DRIVER` (`selenium` | `k6-browser` | `k6-http`).
  The normalisation rule is a single documented function; adjust it once the
  workplace tag format is confirmed.
- **GitHub Actions**: `mvn verify` (builds module + WARs, runs unit tests) +
  a dashboard-JSON lint. No load run in Actions.

---

## 6. Phasing

| Phase | Deliverable | Spikes | Acceptance |
|---|---|---|---|
| **0 ✅ done 2026-09-10** | Repo skeleton, `metrics-api` + no-op, demo `portal-web` with login + form + list, WildFly 26.1 on the VM, Selenium/Java driver running login→form→save, Prometheus + Grafana up, run-scoped OTel Collector wired. See `docs/04-runbook.md`. | — | met: WAR deploys (HTTP 200), 45s Selenium run = 38 iterations / 0 failures, Grafana health OK externally, `/metrics` returns 404 with the toggle off, unit tests green |
| **1** (core) | One backend live (per Spike A), server-side per-action timers keyed off JSF + poll filter, toggle working, collector run-scoped with run labels, Dashboard 1 | A, B, C | acceptance criteria 1–3 in requirements |
| **2** | Second backend behind facade + comparison write-up; second UI driver + client-side cross-check; Dashboards 2 & 3 (overlay + trend); Jenkinsfile | D, F | acceptance criteria 4–6 |
| **3** | `service-a`/`service-b` instrumented + hand-written k6 HTTP service scenarios ("middle" layer) | — | service percentiles in Grafana |
| **4** | App-server/JVM metrics via agent (no app Java); Dashboard 4; porting-notes.md; EAP deltas | E, G | acceptance criteria 7–8 |

Phase 0–1 is the "keep it simple" milestone to demo before going wider.

---

## 7. "Try more than one solution?" — yes, in three bounded places

1. **Instrumentation library** (Micrometer vs Prometheus client) — both built,
   compared, one chosen. Low cost because the facade isolates them.
2. **Run-labelling / export path** — document all three (Collector, Pushgateway,
   k6 remote-write); implement **Collector + k6 remote-write**; reject
   Pushgateway with a written reason. The comparison is itself a showcase
   artefact.

3. **Scenario driver** — implement two (Selenium/Java as primary UI driver, k6
   browser as alternative + client-side cross-check) and one k6 HTTP service
   driver. This is genuinely informative: it proves the server-side metric is
   driver-independent, and it lets the team pick the UI driver they find most
   maintainable without changing anything else. A recorded-HTTP example is kept
   only as a cautionary artefact.

Not proposing to build a second app server or a second dashboards stack — that
spends effort without informing a real decision.

---

## 8. Risks and mitigations

| Risk | Mitigation |
|---|---|
| WildFly's MicroProfile Metrics subsystem clashes with our registry | Spike A decides: disable the subsystem or namespace our metrics; documented for EAP. |
| Laptop freezes lose work | Frequent commits; all real work happens in the repo or on the VM; `infra` scripts are idempotent. |
| Per-run `run_id` cardinality in Prometheus | Few runs; recording rules collapse each run to a summary series; raw series retention kept short. |
| JSF action timing misattributes AJAX/redirect flows, or a driver can't set a request marker | Action identified server-side from `javax.faces.source` / view id (no driver marker needed); Spike B pins the source-id map; correlation token for login; explicit tests. |
| PrimeFaces polling / auto-refresh inflates or pollutes action metrics | Poll component ids configured and filtered (dropped or bucketed as a separate `poll` action); verified in Spike B. |
| Recorded k6 HTTP script for the JSF app is large and brittle | Not the primary UI path; kept only as a cautionary example. UI load uses Selenium/Java or k6 browser (step-based). |
| Collector config per run is fiddly | Env-var-driven `resource` processor, not templated YAML, if Spike C confirms it works. |
| Commercial PrimeFaces theme not redistributable | Free bundled **Saga** theme + `primefaces.THEME` swap point; layout template kept separate (D1). |
| Cloud cost / left-running VM | `down.sh` deletes it; document the check; optional budget alert. |
| Scope creep into JVM/MBean land (repeat of last time) | Phasing makes it explicit and last; phase-2 route uses an agent with **no custom Java to unit-test**. |

---

## 9. Decisions (locked 2026-09-10)

All review points are resolved. Mirrored in `01-requirements.md` §8.

| # | Decision |
|---|---|
| **D1** | Demo UI theme: free bundled PrimeFaces **Saga** (light), with a simple layout template. `primefaces.THEME` in `web.xml` is the documented swap point for a commercial theme; the layout template is separate so it can be swapped independently. |
| **D2** | **No** EAP 7.4 parity check on the VM this phase. Spike G is a desk review only. |
| **D3** | `release` label = `git describe --tags --always`, **normalised** to a bare version (strip leading `v` / `version ` prefix and any trailing `-<n>-g<sha>`; `version 1.0.0` → `1.0.0`). Workplace tag format (Maven release plugin) to be confirmed; normalisation is one documented function and a `RELEASE` param overrides. |
| **D4** | **Recording-rules-only** for trend history. No separate results service. |
| **D5** | Target **JDK 17** (workplace runs EAP 7.4 on JDK 17; WildFly 26.1 supports it). |
| **D6** | Primary UI driver: **Selenium (Java + TestNG)**. k6 browser = alternative + client-side cross-check. k6 HTTP = services. |
| **D7** | GCP: project `mmcnicol-geneology`, `e2-standard-4`, Debian, Grafana port firewalled to the engineer's current public IP only; VM deleted between sessions. |
| **D8** | Phasing per §6: **Phase 0–1 is the agreed first milestone**; services (Phase 3) and JVM metrics (Phase 4) follow. |

### Next steps (Phase 0)

1. `infra/gcp/up.sh` + `bootstrap.sh` — bring up the VM, install JDK 17 / Maven /
   k6 / headless Chrome / WildFly 26.1, stand up Prometheus + Grafana +
   (run-scoped) OTel Collector via docker compose.
2. Repo skeleton: Maven reactor, `metrics-support/metrics-api` + no-op backend,
   `demo-app/portal-web` (Saga theme: login → clinical-form stand-in → list).
3. `load/scenarios/happy-path.md` + a Selenium/Java driver that loops
   login → open-form → save-form.
4. Confirm end-to-end: app deploys, scenario runs, Grafana reachable.

Then Phase 1 (Spikes A/B/C) wires the real instrumentation, the toggle, run
labelling, and Dashboard 1.

I'll begin Phase 0 and report back when the VM + skeleton are up.
7. **GCP** — OK to use project `mmcnicol-geneology` and an `e2-standard-4`, with
   the Grafana port firewalled to your current IP only?
8. Anything in `01-requirements.md` to add, cut, or reword before I treat it as
   the baseline.

Once you approve, I'll start at Phase 0 and bring up the VM.
