# Spike E — App-server / JVM metrics without a testability mess

Status: **complete** (2026-09-10).

**Question.** For JBoss/WildFly + JVM metrics collected only during a run, what
is the least-effort, least-untestable-code route?

**Answer: none of it needs custom Java, and on WildFly/EAP you don't even need a
second agent.**

## Options compared

| Option | Custom Java | 2nd agent in the app JVM | Works on WildFly 26 | Works on Windows | Notes |
|---|---|---|---|---|---|
| **1. MicroProfile Metrics subsystem** (`:9990/metrics`) | none | **no** | ✅ verified | ✅ | Already in the default WildFly **and EAP 7.4** profile. Unauthenticated on the mgmt interface by default. 226 metrics incl. `base_memory_*Heap_bytes`, `base_gc_*`, `base_thread_*`, `base_cpu_*`, `vendor_*`, and `wildfly_undertow_*` per deployment. **Recommended.** |
| **2. jmx_exporter — standalone httpserver via remote JMX** | none | no (separate process) | ✅ verified (200) | ✅ | Connects to `service:jmx:remote+http://localhost:9990` with a read-only mgmt user (`add-user.sh -g monitor`). Some `jboss.as:*` MBeans throw on read over remoting; `java.lang:*` is clean. Use when not on WildFly/EAP or the subsystem is stripped. |
| **3. jmx_exporter — `-javaagent`** | none | **yes** | ❌ **conflicts** | ✅ (in principle) | Both 0.20.0 and 1.6.0 break WildFly 26 boot: `WFLYLOG0078` — the agent initialises `java.util.logging` before WildFly installs `org.jboss.logmanager.LogManager`, so the logging subsystem refuses to start. Also needs `-Djboss.modules.system.pkgs=io.prometheus.jmx`. Solvable (Instana's agent clearly does) but fiddly. |
| **4. OTel Collector JMX receiver** | none | no | (not tested) | ✅ | Collector spawns the `opentelemetry-jmx-metrics` gatherer against a JMX endpoint. Same "needs remote JMX + user" shape as option 2, kept in the one collector we already run. A reasonable future consolidation. |
| **5. Micrometer JVM/JMX binders in-app** | **some** | no | ✅ | ✅ | Needs the metrics module deployed in every WAR and `new JvmGcMetrics().bindTo(registry)` etc. This is the "reinvent with a library" path — more code, more to test, and duplicated per WAR. Rejected. |
| **6. Hand-rolled MBean-reading Java** | **lots** | no | ✅ | ✅ | The route that previously caused the JaCoCo/Sonar headache. Rejected. |

## Recommendation

**Use option 1 (the MicroProfile Metrics subsystem).** For a load test:

1. Confirm the `microprofile-metrics-smallrye` subsystem is present (it is in the
   default EAP 7.4 / WildFly profile).
2. Enable Undertow statistics for the per-deployment request metrics:
   `/subsystem=undertow:write-attribute(name=statistics-enabled,value=true)`.
3. Point the load-test OTel Collector at `:9990/metrics` (job `wildfly`) — it is
   scraped only for the run duration and stamped with `run_id` / `release` like
   everything else.
4. Keep the management port internal (NFR7).

**This is the direct answer to "would apps-mgmt need to install a second
agent?" — no.** The subsystem is already there; nothing is installed; the
scrape happens only during a load test. Option 2 is the fallback if the subsystem
is unavailable, and it's still not an *agent* (no bytecode instrumentation, no
change to the app JVM's args), so it shouldn't collide with the incumbent APM
agent.

Nothing in options 1–4 needs a unit test or a coverage exclusion.

## Delivered

- Collector scrapes `:9990/metrics` (+ an optional `:9404` for option 2).
- Recording rules unchanged (JVM series are gauges; no per-run rollup needed —
  the dashboard filters by `run_id`).
- Dashboard **"App Server & JVM — Load Test"**: heap (used/committed/max), GC
  rate + time fraction, threads, process CPU, loaded classes / non-heap,
  Undertow requests/s and mean processing time + errors by deployment.
- `scripts/on-vm-jmx-exporter.sh on|off` for option 2.
- Verified against a 3-VU / 75 s run: heap 137–158 MB, GC ~0.02/s, CPU ~0.6 %,
  232 `wildfly` series carrying the run labels.

## Not done (per the engineer's instruction 2026-09-10)

`docs/porting-notes.md` / Spike G desk review deferred.
