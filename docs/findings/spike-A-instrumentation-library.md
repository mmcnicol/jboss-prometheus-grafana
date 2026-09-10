# Spike A — Micrometer vs Prometheus Java client

Status: **first pass complete** (Phase 1). Both backends built, wired behind the
same `MetricsBackend` SPI, and run on WildFly 26.1 / JDK 17.

## What was built

| | `metrics-micrometer` | `metrics-prometheus` |
|---|---|---|
| Library | `io.micrometer:micrometer-registry-prometheus:1.13.6` | `io.prometheus:prometheus-metrics-core` + `-exposition-formats:1.3.1` |
| Action metric | `Timer` → `portal_user_action_seconds{action,outcome}` with `.publishPercentileHistogram()` | one `Histogram` with `action,outcome` label names, classic buckets |
| Counter | `Counter` builder, arbitrary tags per call | `Counter` per distinct label-name set (client needs names fixed at registration) |
| Exposition | `registry.scrape()` — one call, returns text | `ExpositionFormats.init().getPrometheusTextFormatWriter().write(out, registry.scrape())` |
| Toggle-off cost | nothing constructed (no-op backend chosen by `MetricsProvider`) | same |

## Observations

- **Dependency weight.** Micrometer 1.13's `micrometer-registry-prometheus`
  now pulls the `io.prometheus` client_java 1.x stack transitively, so the two
  options land on a similar transitive footprint. Micrometer adds
  `micrometer-core` + `micrometer-commons` on top.
- **API ergonomics — timers.** Micrometer's `Timer.builder(...).register(reg)`
  is deduplicated by name+tags, so a "get or create per (action, outcome)" cache
  is trivial. The Prometheus client's single `Histogram` with
  `labelValues(action, outcome).observe(x)` is even simpler for this shape.
- **API ergonomics — counters.** The Prometheus client requires label names
  fixed at registration; the facade's `increment(name, k, v, ...)` needs a
  per-label-set counter cache to bridge that. Micrometer takes ad-hoc tags
  directly. Minor, but Micrometer wins here.
- **Package churn.** Micrometer moved `PrometheusMeterRegistry` from
  `io.micrometer.prometheus` to `io.micrometer.prometheusmetrics` in 1.13 — a
  one-time migration cost, noted for anyone on an older Micrometer.
- **Percentile histogram.** Micrometer's `.publishPercentileHistogram()` emits
  Prometheus-native `le` buckets → `histogram_quantile()` works in Grafana
  (verified: p95 `login` ≈ 0.133 s against a 120 ms simulated delay). The
  Prometheus client's classic buckets do the same; bucket boundaries are
  explicit in our code either way.
- **WildFly MicroProfile Metrics.** No collision seen: our endpoint is at
  `/portal-web/metrics` (app context), WildFly's MP Metrics is on the
  management interface. Disabling the `microprofile-metrics-smallrye` subsystem
  is **not** required for this design. (Left as a note for the EAP porting doc.)
- **Testability.** Both backends unit-test cleanly by scraping into a string and
  asserting on the exposition — no container, no static state. Nothing needs a
  JaCoCo exclusion.

## Recommendation (provisional)

**Default to Micrometer** (`metrics-micrometer`), keep `metrics-prometheus`
behind the SPI as the proof that the facade is backend-neutral.

Reasons: aligns with the tech lead's Spring Boot / Micrometer direction (D-context);
ad-hoc counter tags; large ecosystem of ready-made binders for the Phase 4
JVM/app-server metrics if we ever want them in-process. The Prometheus client is
a perfectly good fallback and is marginally simpler for pure histograms.

Revisit if: the JVM/app-server metrics decision (Spike E) lands on an agent
(no in-process binders needed), which weakens Micrometer's main advantage.
