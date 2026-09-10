#!/usr/bin/env bash
# Runs ON the demo VM. One labelled load-test run:
#   1. start the OTel Collector scoped to this run (adds run_id/release/... labels)
#   2. run the scenario with a driver
#   3. stop the collector
#
#   ./load/run-loadtest.sh [--driver selenium|k6-browser] [--vus N] [--duration S] [--release X]
#
# Phase 0: the app exposes no /metrics yet, so the collector scrapes nothing.
# The plumbing is exercised end to end; real series arrive in Phase 1.
set -euo pipefail
cd "$(dirname "$0")/.."

DRIVER=selenium
VUS=2
DURATION=60
RELEASE="$(git describe --tags --always 2>/dev/null || echo unknown)"
# normalise: strip leading v / 'version ' and any trailing -<n>-g<sha> (decision D3)
RELEASE="$(echo "$RELEASE" | sed -E 's/^v//; s/^version //; s/-[0-9]+-g[0-9a-f]+$//')"
TEST_TYPE=ui
BASE_URL="http://localhost:8080/portal-web"

while [ $# -gt 0 ]; do
  case "$1" in
    --driver)   DRIVER="$2"; shift 2 ;;
    --vus)      VUS="$2"; shift 2 ;;
    --duration) DURATION="$2"; shift 2 ;;
    --release)  RELEASE="$2"; shift 2 ;;
    --base-url) BASE_URL="$2"; shift 2 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

RUN_ID="${JOB_NAME:-local}-$(date -u +%Y%m%dT%H%M%SZ)"
export RUN_ID RELEASE TEST_TYPE
echo "run_id=$RUN_ID release=$RELEASE driver=$DRIVER vus=$VUS duration=${DURATION}s"

DC=(sudo -E docker compose -f observability/docker-compose.yml --profile loadtest)

# Stop ONLY the collector — leave Prometheus + Grafana running.
cleanup() { "${DC[@]}" rm -sf otel-collector >/dev/null 2>&1 || true; }
trap cleanup EXIT

echo "== start collector =="
"${DC[@]}" up -d otel-collector
sleep 3

echo "== run scenario =="
case "$DRIVER" in
  selenium)
    mvn -q -B -pl load/selenium-java exec:java \
      -DbaseUrl="$BASE_URL" -Dvus="$VUS" -DdurationSeconds="$DURATION" ;;
  k6-browser)
    echo "k6-browser driver arrives in Phase 2" >&2; exit 3 ;;
  *)
    echo "unknown driver: $DRIVER" >&2; exit 2 ;;
esac

echo "== settle 20s (let the last scrape land) =="
sleep 20

echo "== stop collector =="
# handled by trap
echo "done: $RUN_ID"
