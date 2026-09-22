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

---

## 5. Check the Undertow request panels for double-counting

**Why.** `wildfly_undertow_request_count_total` is probably reported at two
levels: per servlet (labels `deployment`, `servlet`) and per HTTP listener
(labels `server`, `http_listener`). The existing panels sum it without
filtering by level. If both levels are present:
- "requests/s by deployment" gains an extra series with an empty
  `deployment` label (the listener total);
- "mean processing time" divides listener-only processing time by
  servlet + listener requests, so it reads roughly half the true value.

**Check.** On the VM, during a run:
`curl -s localhost:9990/metrics | grep '^wildfly_undertow_request_count_total'`
and note which label sets appear.

**Fix, if confirmed.** Filter each query to a single level: `servlet!=""`
for per-deployment rates, and `http_listener!=""` for the mean-time
calculation, so it matches `processing_time` and `error_count`. Close with no
change if only one level is present.

---

## 6. Give the demo app real datasource traffic

**Why.** The datasource pool panel, and items 1 and 2, stay flat because
the demo never touches a database. `DischargeStore` and `PatientDirectory`
only sleep to simulate latency. Without real traffic, those panels cannot
be shown working under load.

**Change.** Have one service (service-a's `PatientDirectory` is the smallest)
run a real query against WildFly's built-in `ExampleDS` (in-memory H2)
instead of sleeping. It could be a plain JDBC `SELECT` via
`@Resource(lookup = "java:jboss/datasources/ExampleDS")`, or a single JPA
entity. Keep the existing simulated delay configurable so latency figures
stay comparable with earlier runs.

**Done when.** Under a k6 run, the pool panel shows `in use` > 0 for
`ExampleDS`, and `created` rises from 0. Optionally, shrink the `ExampleDS`
max pool size to see waits appear in item 2's panel.

**Keep it small.** No schema tooling and no migration framework: create one
table at startup, or use Hibernate's `hbm2ddl` if JPA is chosen.

---

## 7. Session passivation and activation (Infinispan)

**Why.** When there are more sessions than the in-memory limit, WildFly
*passivates* the least-recently-used ones: it serializes them out of the
heap to a store. When a user returns, the session is *activated*, which
means read back and deserialized. Under load, this costs serialization time
and disk I/O on the request path, and it grows with session size (see
JSF view state). A high passivation rate during a run means the session
limit is too low for the user count; activations show users paying the
cost of getting their session back.

**Check first: does it apply?** Passivation only happens for
**distributable** web apps (`<distributable/>` in `web.xml`), whose sessions
Infinispan manages. Without it, Undertow keeps every session in memory and
there is nothing to passivate. None of the demo WARs is distributable today.
Confirm whether the real app is before building this.

**Prerequisites.**
- `<distributable/>` in the WAR's `web.xml`, and all session attributes
  `Serializable`.
- A session limit that load can exceed: `max-active-sessions` in
  `jboss-web.xml`, or the cache's memory `size`.
- Infinispan statistics on the cache backing web sessions. This is the
  `web` container's cache (the `passivation` local cache in the standalone
  profile, `dist` in HA), for example:
  `/subsystem=infinispan/cache-container=web/local-cache=passivation:write-attribute(name=statistics-enabled,value=true)`.
  Add to `on-vm-enable-statistics.sh`.

**Candidate metrics** (labels likely `cache_container`, cache name).
The attribute names and the resource that holds them (the cache or a
`component=persistence` child) vary by WildFly version, so verify both:
- `wildfly_infinispan_passivations`: counter
- `wildfly_infinispan_activations`: counter
- `wildfly_infinispan_number_of_entries`: sessions currently in memory
- optionally `wildfly_infinispan_stores` and `average_write_time` for the
  cost side

**Panel.** "Sessions — passivation / activation": passivations and
activations per second, with in-memory entries on the right axis, next to
the active-sessions panel.

**Demo.** To show it working, mark portal-web `<distributable/>` and set a
low `max-active-sessions` (for example 20) so that a modest k6 run exceeds
it. Watch for `NotSerializableException` in the log; the session beans must
be `Serializable`.
