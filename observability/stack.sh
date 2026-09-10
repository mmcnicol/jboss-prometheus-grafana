#!/usr/bin/env bash
# Manage the always-on observability stack (Prometheus + Grafana) on the VM.
#   ./observability/stack.sh up | down | ps | logs
set -euo pipefail
cd "$(dirname "$0")"

DC=(sudo docker compose -f docker-compose.yml)

case "${1:-up}" in
  up)   "${DC[@]}" up -d prometheus grafana ;;
  down) "${DC[@]}" down ;;
  ps)   "${DC[@]}" --profile loadtest ps ;;
  logs) shift; "${DC[@]}" logs -f "$@" ;;
  *)    echo "usage: $0 {up|down|ps|logs}" >&2; exit 2 ;;
esac
