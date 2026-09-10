#!/usr/bin/env bash
# Runs ON the demo VM. One labelled load-test run:
#   1. enable app metrics (restore prior state afterwards)
#   2. start the OTel Collector scoped to this run (adds run_id/release/... labels)
#   3. run the scenario with a driver
#   4. stop the collector
#
#   ./load/run-loadtest.sh [--driver selenium|k6-browser] [--vus N] [--duration S]
#                          [--release X] [--keep-metrics]
set -euo pipefail
cd "$(dirname "$0")/.."

DRIVER=selenium
VUS=2
DURATION=60
RELEASE="$(git describe --tags --always 2>/dev/null || echo unknown)"
RELEASE="$(echo "$RELEASE" | sed -E 's/^v//; s/^version //; s/-[0-9]+-g[0-9a-f]+$//')"   # decision D3
TEST_TYPE=ui
BASE_URL="http://localhost:8080/portal-web"
KEEP_METRICS=0
PROM_RW="http://localhost:9090/api/v1/write"

while [ $# -gt 0 ]; do
  case "$1" in
    --driver)       DRIVER="$2"; shift 2 ;;
    --vus)          VUS="$2"; shift 2 ;;
    --duration)     DURATION="$2"; shift 2 ;;
    --release)      RELEASE="$2"; shift 2 ;;
    --base-url)     BASE_URL="$2"; shift 2 ;;
    --keep-metrics) KEEP_METRICS=1; shift ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

RUN_ID="${JOB_NAME:-local}-$(date -u +%Y%m%dT%H%M%SZ)"
export RUN_ID RELEASE TEST_TYPE
echo "run_id=$RUN_ID release=$RELEASE driver=$DRIVER vus=$VUS duration=${DURATION}s"

UNIT=/etc/systemd/system/wildfly.service
METRICS_WAS="$(grep -oE 'portal\.metrics\.enabled=(true|false)' "$UNIT" | cut -d= -f2)"

DC=(sudo -E docker compose -f observability/docker-compose.yml --profile loadtest)

cleanup() {
  "${DC[@]}" rm -sf otel-collector >/dev/null 2>&1 || true          # collector only
  if [ "$KEEP_METRICS" = "0" ] && [ "$METRICS_WAS" = "false" ]; then
    ./scripts/on-vm-set-metrics.sh off >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

if [ "$METRICS_WAS" != "true" ]; then
  echo "== enable app metrics for this run =="
  ./scripts/on-vm-set-metrics.sh on
fi

echo "== start collector =="
"${DC[@]}" up -d otel-collector
sleep 3

echo "== run scenario ($DRIVER) =="
case "$DRIVER" in
  selenium)
    mvn -q -B -pl load/selenium-java exec:java \
      -DbaseUrl="$BASE_URL" -Dvus="$VUS" -DdurationSeconds="$DURATION" ;;
  k6-browser)
    K6_BROWSER_EXECUTABLE_PATH=/usr/bin/chromium \
    K6_PROMETHEUS_RW_SERVER_URL="$PROM_RW" \
    K6_PROMETHEUS_RW_TREND_STATS="p(50),p(90),p(95),p(99),avg" \
    BASE_URL="$BASE_URL" VUS="$VUS" DURATION="${DURATION}s" \
    RUN_ID="$RUN_ID" RELEASE="$RELEASE" TEST_TYPE="$TEST_TYPE" \
    k6 run --quiet -o experimental-prometheus-rw \
      --tag run_id="$RUN_ID" --tag release="$RELEASE" --tag test_type="$TEST_TYPE" \
      load/k6/browser/scenario.js ;;
  *)
    echo "unknown driver: $DRIVER (selenium | k6-browser)" >&2; exit 2 ;;
esac

echo "== settle 20s (let the last scrape land) =="
sleep 20

echo "== stop collector, restore metrics state =="
# handled by trap
echo "done: $RUN_ID"
