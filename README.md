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

**Phase 3 complete** (2026-09-10) — two microservice WARs (`service-a`,
`service-b`) instrumented via a shared JAX-RS timing filter, a hand-written k6
HTTP service driver, and a **Service Endpoints** dashboard. That's the full
top-to-middle load-test picture. Phase 4 next: app-server / JVM metrics via an
agent (no app code) + EAP porting notes.

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
metrics-support/metrics-servlet      the /metrics scrape endpoint (shared)
metrics-support/metrics-jaxrs        JAX-RS request-timing filter (service_endpoint_seconds)
demo-app/portal-web                  JSF 2.3 + PrimeFaces 13 WAR: login -> discharge form -> list
demo-app/service-a, service-b        JAX-RS microservice WARs ("middle" layer)
load/selenium-java                   primary UI scenario driver (Selenium + Java)
load/k6/browser                      alternative UI driver + client-side cross-check
load/k6/http-services                service-endpoint load driver
load/scenarios                       driver-agnostic scenario step lists
load/run-loadtest.sh                 one labelled run: metrics on -> collector -> scenario -> restore
observability/                       docker-compose: Prometheus (+rules) + Grafana (4 dashboards) + OTel Collector
ci/Jenkinsfile                       parameterised labelled load-test run
infra/gcp/                            create / bootstrap / tear down the demo VM
```
