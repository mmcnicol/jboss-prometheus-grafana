# Backlog — further JBoss/WildFly metrics

Candidates for the **App Server & JVM — Load Test** dashboard
(`observability/grafana/provisioning/dashboards/json/app-server-jvm.json`),
in rough order of value for load testing. All come from the MicroProfile
Metrics subsystem on `:9990/metrics` (Spike E): no agent, no application code.

**Metric names are unverified.** They follow the `wildfly_<subsystem>_<attribute>`
pattern already used by the dashboard, but none has been seen on a running
server. Counters may carry a `_total` suffix. Before building a panel, confirm
the name with `curl -s localhost:9990/metrics | grep <prefix>` on the VM, or
add it to the check list in `scripts/on-vm-enable-statistics.sh`.

Each item is done when: the metric appears on `:9990/metrics` with the run
labels, the panel shows data during a load-test run, and the panel has a
one-line `description` saying what a bad value looks like.

---

## 1. Transactions

**Why.** Timeouts and rollbacks under load often show up here before they
show up as user-visible errors. In-flight transactions climbing with no
matching rise in throughput points to lock contention or a slow backend.

**Prerequisite.** `/subsystem=transactions` `statistics-enabled=true`. Already
set by `on-vm-enable-statistics.sh`.

**Candidate metrics.**
- `wildfly_transactions_number_of_inflight_transactions`: gauge
- `wildfly_transactions_number_of_committed_transactions`: counter, shown as a rate
- `wildfly_transactions_number_of_timed_out_transactions`: counter
- `wildfly_transactions_number_of_aborted_transactions`: counter
- `wildfly_transactions_number_of_application_rollbacks`: counter
- `wildfly_transactions_number_of_resource_rollbacks`: counter

**Panel.** "Transactions": in-flight on the left axis, and timed-out,
aborted and rollbacks per second on the right.

**Caveat.** The demo app uses no transactions, so the lines stay flat
until it does (see item 2's caveat).

---

## 2. Datasource pool waiting

**Why.** The existing pool panel shows how full the pool is. This shows
whether requests are *waiting* for a connection, which is the actual
bottleneck signal. A pool at max with zero waits is fine; waits climbing
is not.

**Prerequisite.** `statistics-enabled=true` on each datasource. Already set
by `on-vm-enable-statistics.sh`.

**Candidate metrics** (label `data_source`):
- `wildfly_datasources_pool_wait_count`: requests that had to wait
- `wildfly_datasources_pool_average_blocking_time`: ms
- `wildfly_datasources_pool_max_wait_time`: ms
- `wildfly_datasources_pool_timed_out`: requests that gave up
- `wildfly_datasources_pool_blocking_failure_count`

**Panel.** "Datasource pools — waiting": waits per second and timeouts,
with average blocking time on a right-hand ms axis. Also a candidate for a
green/red tile on `status.json` (timeouts > 0 → red).

**Caveat.** The demo app has no real database; the only pool is WildFly's
idle `ExampleDS`. To see movement under load, one demo service would need
to query `ExampleDS`.

---

## 3. Session churn

**Why.** It complements the active-sessions panel. Rejected sessions mean
`max-active-sessions` is being hit. A high created rate with low active
sessions means sessions are being dropped and recreated, for example when a
load script loses its cookie.

**Prerequisite.** Undertow `statistics-enabled=true`. Already set.

**Candidate metrics** (label `deployment`):
- `wildfly_undertow_sessions_created`: counter
- `wildfly_undertow_expired_sessions`: counter
- `wildfly_undertow_rejected_sessions`: counter
- `wildfly_undertow_highest_session_count`: gauge

**Panel.** "Undertow — session churn": created, expired and rejected
per second by deployment.

---

## 4. HTTP listener traffic

**Why.** Bytes sent per request growing between releases is an early sign
of page bloat. With JSF/PrimeFaces, the usual cause is view state or large
partial responses. The longest request time also catches outliers that p95
hides.

**Prerequisite.** Undertow `statistics-enabled=true`. Already set.

**Candidate metrics** (labels `server`, `http_listener`):
- `wildfly_undertow_bytes_sent`: counter
- `wildfly_undertow_bytes_received`: counter
- `wildfly_undertow_max_processing_time`: gauge

**Panel.** "Undertow — traffic per listener": bytes sent and received per
second, plus bytes sent per request.
