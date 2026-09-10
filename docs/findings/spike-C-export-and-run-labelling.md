# Spike C — Export path and per-run labelling

Status: **first pass complete** (Phase 1). OpenTelemetry Collector implemented
and verified; Pushgateway and k6 remote-write assessed on paper.

## Implemented: OTel Collector, lifetime = run

`observability/otel-collector/config.yaml`, started by `load/run-loadtest.sh`
for the duration of a run and removed afterwards (Prometheus + Grafana stay up):

```
prometheus receiver  ->  resource processor      ->  prometheusremotewrite -> Prometheus
(scrape                  (upsert run_id, release,     (+ resource_to_telemetry
 /portal-web/metrics      test_type, env from env      _conversion: attrs -> labels)
 every 5s)                vars set per run)
```

- Run identity is set as **environment variables** on the collector container
  (`RUN_ID`, `RELEASE`, `TEST_TYPE`, `ENV`) — the app build is untouched between
  runs (requirement FR9).
- `RELEASE` is normalised to a bare version in `run-loadtest.sh` (decision D3).
- `RUN_ID` = `${JOB_NAME:-local}-<UTC timestamp>`.

**Verified:** after two runs (`release=0.1.0`, `release=0.2.0`), Prometheus holds
`portal_user_action_seconds_*` series for both, each carrying
`run_id`, `release`, `test_type`, `env`. Grafana's "User Actions — Load Test"
dashboard filters by `run_id` and renders throughput / error rate / p95.

### Label hygiene

The Prometheus receiver adds scrape-transport resource attributes
(`http.scheme`, `url.scheme`, `net.host.port`, `server.port`,
`service.name`, `service.instance.id`). We `delete` the first four in the
`resource` processor. `service.name`/`service.instance.id` are kept (harmless,
sometimes useful). These are constant per run and never broke an aggregation.

### "Only during a run"

The collector is **not** in the always-on compose stack — it is a `loadtest`
compose profile. `./observability/stack.sh` only ever manages `prometheus` and
`grafana`. When no run is active nothing scrapes the app, and with the toggle off
the app serves no `/metrics` at all.

## Assessed, not implemented

| Option | Verdict | Why |
|---|---|---|
| **Prometheus Pushgateway** | **rejected** | Built for short batch jobs pushing a final value; a load test produces a time series, not a summary. Grouping-key lifecycle is awkward, and it cannot carry the door open to JBoss/JVM scrape targets later. This matches the engineer's earlier experience (moved off Pushgateway to the Collector). |
| **k6 `--out experimental-prometheus-rw`** | **keep for Phase 2** | Zero app change, client-side truth, same `run_id`/`release` tags via `--tag`. But it is only k6's view and cannot be extended to server-internal / JVM metrics. Complements the collector; does not replace it. The Selenium driver (primary UI driver, D6) emits no metrics of its own, so the server-side timer remains the source of truth. |

## Follow-ups

- Add k6 remote-write alongside the collector in Phase 2 and compare client vs
  server latency for the same action.
- Consider pinning the collector image and adding a `memory_limiter` processor
  for long runs.
- Recording rules for the release-trend summary series land in Phase 2 (D4).
