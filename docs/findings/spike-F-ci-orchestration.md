# Spike F — CI orchestration of a labelled run

Status: **first pass complete** (Phase 2). `ci/Jenkinsfile` +
`.github/workflows/build.yml`, both driven by the same `load/run-loadtest.sh`.

## Split

| | Jenkins (`ci/Jenkinsfile`) | GitHub Actions (`.github/workflows/build.yml`) |
|---|---|---|
| Trigger | manual / scheduled | push + PR |
| Runs a load test | **yes** | no |
| Needs | the demo VM (JDK 17, Maven, Docker, k6, Chromium) as the agent | hosted runner |
| Does | resolve release → build+deploy → labelled run → snapshot panel → archive | `mvn verify` (default backend) + compile the Prometheus-client backend + lint dashboard JSON / YAML |

## Jenkins pipeline

Parameters: `RELEASE` (blank → `git describe` normalised per D3), `DRIVER`
(`selenium` | `k6-browser`), `VUS`, `DURATION`, `GRAFANA`, `DASHBOARD_UID`.

Stages: `Checkout` → `Resolve release` → `Build & deploy`
(`scripts/on-vm-build-deploy.sh`) → `Load test`
(`load/run-loadtest.sh` with `JOB_NAME` exported so `run_id` =
`<job>-<build>-<timestamp>`) → `Snapshot dashboard` (Grafana
`/render/d-solo/...panelId=3` PNG + dashboard JSON export) →
`archiveArtifacts`.

`run-loadtest.sh` already: enables the metrics toggle for the run and restores
the prior state; starts the Collector scoped to the run; stops only the
Collector afterwards (Prometheus + Grafana stay up).

## Notes / follow-ups

- The PNG render needs the Grafana **image-renderer** plugin (or the
  `grafana-image-renderer` sidecar). Without it the stage still archives the
  dashboard JSON. Add the renderer to `observability/docker-compose.yml` if the
  snapshot is wanted in every build.
- `run_id` scheme confirmed: `${JOB_NAME:-local}-<UTC timestamp>`; Jenkins
  passes `JOB_NAME` so runs are traceable to a build.
- A scheduled daily 10-minute run (as the engineer had previously) is just this
  job on a `cron` trigger with `DURATION=600`.
- No secrets in the pipeline; Grafana admin/admin is the demo default and must
  be changed for any shared instance (`GF_SECURITY_ADMIN_PASSWORD`).
