#!/usr/bin/env bash
# Runs ON the demo VM. Flips portal.metrics.enabled in the WildFly unit and
# restarts. A restart to toggle metrics is acceptable for load-test envs (FR2).
#   ./scripts/on-vm-set-metrics.sh on|off
set -euo pipefail

case "${1:-}" in
  on)  want=true ;;
  off) want=false ;;
  *)   echo "usage: $0 on|off" >&2; exit 2 ;;
esac

unit=/etc/systemd/system/wildfly.service
sudo sed -i -E "s/portal\.metrics\.enabled=(true|false)/portal.metrics.enabled=${want}/" "$unit"
sudo systemctl daemon-reload
sudo systemctl restart wildfly

for i in $(seq 1 40); do
  c=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/portal-web/login.xhtml || true)
  [ "$c" = "200" ] && break
  sleep 1
done
m=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/portal-web/metrics || true)
echo "portal.metrics.enabled=${want}  ->  /portal-web/metrics = ${m}  (200 when on, 404 when off)"
