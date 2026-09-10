# jboss-prometheus-grafana

A **generic reference implementation** for getting application metrics out of a
JBoss / Jakarta EE + JSF/PrimeFaces web application into **Prometheus** and
**Grafana** *during a load test* — not for production monitoring.

It shows how to:

- instrument key **user actions** (e.g. login click → next page rendered) behind
  a JBoss system-property toggle, so the app only emits metrics when asked;
- collect metrics **only for the duration of a load-test run**, tagging every
  sample with `run_id` / `release`;
- drive load with **k6**;
- build a Grafana **baseline-vs-candidate overlay** and a **release trend** chart.

WildFly 26.1 is used as the redistributable stand-in for JBoss EAP 7.4. No
licensed binaries or workplace-specific detail are included.

## Status

**Phase 1 complete** (2026-09-10) — instrumentation behind the
`portal.metrics.enabled` toggle (Micrometer default, Prometheus-client alt), the
OTel Collector stamps each run with `run_id` / `release`, and the "User Actions —
Load Test" Grafana dashboard renders per-action throughput / error rate / p95.
Phase 2 next: second UI driver + client cross-check, baseline-vs-candidate
overlay, release-trend panel, Jenkinsfile.

- [`docs/01-requirements.md`](docs/01-requirements.md)
- [`docs/02-spikes.md`](docs/02-spikes.md)
- [`docs/03-technical-solution.md`](docs/03-technical-solution.md) — decisions in §9, phasing in §6
- [`docs/04-runbook.md`](docs/04-runbook.md) — bring the stack up / down
- [`docs/findings/`](docs/findings/) — spike results

## Layout

```
metrics-support/metrics-api          portable timing facade + system-property toggle + no-op
metrics-support/metrics-micrometer   backend: Micrometer + Prometheus registry (default)
metrics-support/metrics-prometheus   backend: Prometheus Java client (alt, -Dbackend=prometheus-client)
demo-app/portal-web                  JSF 2.3 + PrimeFaces 13 WAR: login -> discharge form -> list
load/selenium-java                   primary UI scenario driver (Selenium + Java)
load/scenarios                       driver-agnostic scenario step lists
load/run-loadtest.sh                 one labelled run: collector up -> scenario -> collector down
observability/                       docker-compose: Prometheus + Grafana + (run-scoped) OTel Collector
infra/gcp/                            create / bootstrap / tear down the demo VM
```
