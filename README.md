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

Technical solution **approved** (2026-09-10). Implementation starting at Phase 0
(VM + repo skeleton). See `docs/`:

1. [`docs/01-requirements.md`](docs/01-requirements.md)
2. [`docs/02-spikes.md`](docs/02-spikes.md)
3. [`docs/03-technical-solution.md`](docs/03-technical-solution.md) — approved; decisions in §9
