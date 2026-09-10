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

**Phase 0 complete** (2026-09-10) — WildFly 26.1 + demo app + Selenium driver +
Prometheus/Grafana all running on a GCP VM. Phase 1 next: real instrumentation
behind the toggle, run labels, first dashboard.

- [`docs/01-requirements.md`](docs/01-requirements.md)
- [`docs/02-spikes.md`](docs/02-spikes.md)
- [`docs/03-technical-solution.md`](docs/03-technical-solution.md) — decisions in §9, phasing in §6
- [`docs/04-runbook.md`](docs/04-runbook.md) — bring the stack up / down

## Layout

```
metrics-support/metrics-api   portable timing facade + system-property toggle + no-op backend
demo-app/portal-web           JSF 2.3 + PrimeFaces 13 WAR: login -> discharge form -> list
load/selenium-java            primary UI scenario driver (Selenium + Java)
load/scenarios                driver-agnostic scenario step lists
observability/                docker-compose: Prometheus + Grafana + (run-scoped) OTel Collector
infra/gcp/                     create / bootstrap / tear down the demo VM
```
