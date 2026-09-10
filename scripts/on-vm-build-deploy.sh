#!/usr/bin/env bash
# Runs ON the demo VM. Builds the reactor and deploys portal-web to WildFly.
#   cd ~/jboss-prometheus-grafana && ./scripts/on-vm-build-deploy.sh
set -euo pipefail
cd "$(dirname "$0")/.."

WFLY_DEPLOYMENTS="${WFLY_DEPLOYMENTS:-/opt/wildfly/standalone/deployments}"

echo "== build =="
mvn -q -B -DskipTests package

war="demo-app/portal-web/target/portal-web.war"
[ -f "$war" ] || { echo "missing $war" >&2; exit 1; }

echo "== deploy $war =="
sudo install -o wildfly -g wildfly -m 0644 "$war" "$WFLY_DEPLOYMENTS/portal-web.war"

echo "== wait for deployment =="
for i in $(seq 1 60); do
  if [ -f "$WFLY_DEPLOYMENTS/portal-web.war.deployed" ]; then
    echo "deployed after ${i}s"; break
  fi
  if [ -f "$WFLY_DEPLOYMENTS/portal-web.war.failed" ]; then
    echo "DEPLOYMENT FAILED:"; sudo cat "$WFLY_DEPLOYMENTS/portal-web.war.failed"; exit 1
  fi
  sleep 1
done

code="$(curl -fsS -o /dev/null -w '%{http_code}' http://localhost:8080/portal-web/login.xhtml || true)"
echo "GET /portal-web/login.xhtml -> $code"
[ "$code" = "200" ] || { echo "app not serving login page" >&2; exit 1; }
echo "OK"
