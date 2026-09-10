# Phase 3 — service ("middle") layer

Status: **complete** (2026-09-10). No spike — a straight build on the Phase 1/2
pattern.

## What was added

- **`metrics-api`**: `EndpointTimer` + `Metrics.endpoint(service, route, method)`
  → `service_endpoint_seconds{service, route, method, status, outcome}`
  (histogram). Implemented in both backends and the no-op.
- **`metrics-support/metrics-servlet`**: `MetricsServlet` moved here (with a
  `META-INF/web-fragment.xml` so the container scans the jar for the
  `@WebServlet`). Shared by all three deployables.
- **`metrics-support/metrics-jaxrs`**: `EndpointTimingFilter` (`@Provider`,
  request + response filter). `route` is the matched **path template** built
  from the class + method `@Path` annotations via `ResourceInfo`
  (`/patients/{ref}`, not `/patients/PT-01234`) — so ids don't explode
  cardinality. `service` from the `metrics.service.name` context init-param.
- **`demo-app/service-a`** (patient directory) and **`demo-app/service-b`**
  (codes + validate). Thin JAX-RS resources over plain domain classes
  (`PatientDirectory`, `CodeRegistry`, `DischargeValidator`) — the plain classes
  are fully unit-tested; the resources are glue.
- **Collector**: now scrapes all three apps (`/portal-web/metrics`,
  `/service-a/metrics`, `/service-b/metrics`).
- **Recording rules**: `run:service_endpoint_seconds:p95/p99`,
  `run:service_endpoint:rate`, `:error_ratio`.
- **`load/k6/http-services/scenario.js`** + `run-loadtest.sh --driver k6-http`
  (`test_type=service`). Hand-written, tagged `name` per endpoint so k6's own
  `k6_http_req_duration{name=...}` lines up with the server-side route.
- **Dashboard "Service Endpoints — Load Test"**: throughput / error ratio / p95
  by endpoint, client-vs-server p95, per-endpoint summary table.

## Notes

- **Testing at the JAX-RS boundary**: `new NotFoundException(...)` and
  `Response.ok(...)` need a JAX-RS `RuntimeDelegate` that isn't on the unit-test
  classpath. Rather than pull a JAX-RS impl into test scope, the logic lives in
  plain classes and the resources stay thin — the small untestable surface is
  explicit (mirrors the FR20 approach for MBean code).
- **k6 units**: `k6_http_req_duration_*` from the Prometheus-RW output is in
  **seconds** (0.011 = 11 ms), unlike the text summary which prints ms. Dashboard
  queries use it directly, no `/1000`.
- Verified: 6-VU / 90 s `k6-http` run → 985–986 requests per endpoint in
  Prometheus; server-side p95 `GET /service-a/patients` 21.7 ms,
  `GET /service-b/codes` 9.6 ms (consistent with the simulated delays);
  `http_req_failed` 8.4 % matches the deliberate 404/400 mix.
