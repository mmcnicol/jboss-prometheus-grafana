# Spike D — Grafana baseline-vs-candidate overlay and release trend

Status: **first pass complete** (Phase 2). Two dashboards provisioned from
checked-in JSON, backed by recording rules.

## Recording rules (decision D4 — rules only, no results store)

`observability/prometheus/rules/loadtest.yml`, evaluated every 15s, each
aggregating by `(action, run_id, release, test_type)`:

| rule | meaning |
|---|---|
| `run:portal_user_action_seconds:p50` / `:p95` / `:p99` | `histogram_quantile` over a 2m window |
| `run:portal_user_action:rate` | iterations/sec |
| `run:portal_user_action:error_ratio` | failed / total |

A run's raw series only exist while the Collector is pushing (the run window), so
these derived series are populated only then. That is what makes "one point per
run" work when the panel looks back over weeks.

## Dashboard 3 — "Release Trend"

- **p95 of `$action` across runs**: `max by (release) (run:portal_user_action_seconds:p95{action="$action"})`
  over `now-30d`, rendered as **points** (`drawStyle: points`, `spanNulls:
  false`). Each run is a small cluster of points at its wall-clock time; a
  regression between releases is visible immediately.
- **Peak p95 / peak error ratio per release** tables:
  `max by (release) (max_over_time(...[$__range]))`.

## Dashboard 2 — "Baseline vs Candidate"

Variables: `$baseline`, `$candidate` (both `run_id`), `$action` (multi),
`$shift` (textbox).

| panel | technique | alignment issue? |
|---|---|---|
| **p95 by action — bar chart** | instant `max_over_time(...p95{run_id=$baseline}[$__range])` vs same for `$candidate`, joined on `action` | none — whole-run aggregate |
| **Delta table** | same two queries + `calculateField` for `Δ p95` and `Δ p95 %`, colour-graded | none |
| **p95 over time — overlay** | candidate plotted as-is; baseline plotted with PromQL `offset -$shift` to slide it onto the candidate window | **yes** — `$shift` must be set to ≈ (candidate start − baseline start) |
| **client vs server p95** | `k6_client_action_seconds_p95` (k6 browser) vs `run:portal_user_action_seconds:p95` | none; only populated for k6-browser runs |

### Verdict on the overlay techniques (from Spike C options a/b/c)

- **(a) Grafana per-query time-shift** — no first-class per-target time shift in
  Grafana 11 for Prometheus; would need panel-level shift (applies to all
  queries). Rejected.
- **(b) record an `elapsed_seconds` axis at collection time** — cleanest result
  (both runs start at x=0) but needs the app or collector to emit elapsed time as
  a metric/label. Deferred; revisit if the offset approach proves annoying.
- **(c) template vars + `offset`** — **chosen for now.** `$shift` is a manual
  knob, but the bar chart + delta table (the panels people actually read for a
  release comparison) are alignment-free, so the overlay is a secondary view.

## Follow-ups

- If teams want a true elapsed-time overlay, add a recording/relabel step that
  stamps `t_minus_start` and switch panel 3 to a `... by (elapsed)` query.
- Auto-suggest `$shift` from `(timestamp(candidate) - timestamp(baseline))`.
