#!/usr/bin/env bash
# Runs ON the demo VM. Builds the reactor and deploys the demo WARs to WildFly.
#   cd ~/jboss-prometheus-grafana && ./scripts/on-vm-build-deploy.sh [war-name ...]
# With no args, deploys all three: portal-web, service-a, service-b.
set -euo pipefail
cd "$(dirname "$0")/.."

WFLY_DEPLOYMENTS="${WFLY_DEPLOYMENTS:-/opt/wildfly/standalone/deployments}"
WARS=("$@")
[ ${#WARS[@]} -eq 0 ] && WARS=(portal-web service-a service-b)

echo "== build =="
mvn -q -B -DskipTests package

deploy_one() {
  local name="$1"
  local war
  war="$(find "demo-app/$name/target" -maxdepth 1 -name "$name.war" 2>/dev/null | head -1)"
  [ -f "$war" ] || { echo "missing $name.war" >&2; return 1; }
  sudo install -o wildfly -g wildfly -m 0644 "$war" "$WFLY_DEPLOYMENTS/$name.war"
  for _ in $(seq 1 60); do
    [ -f "$WFLY_DEPLOYMENTS/$name.war.deployed" ] && { echo "$name: deployed"; return 0; }
    if [ -f "$WFLY_DEPLOYMENTS/$name.war.failed" ]; then
      echo "$name: DEPLOYMENT FAILED"; sudo cat "$WFLY_DEPLOYMENTS/$name.war.failed"; return 1
    fi
    sleep 1
  done
  echo "$name: timed out waiting for deployment" >&2; return 1
}

for w in "${WARS[@]}"; do
  echo "== deploy $w =="
  deploy_one "$w"
done

echo "== smoke =="
declare -A URL=(
  [portal-web]="http://localhost:8080/portal-web/login.xhtml"
  [service-a]="http://localhost:8080/service-a/api/patients?limit=1"
  [service-b]="http://localhost:8080/service-b/api/codes"
)
rc=0
for w in "${WARS[@]}"; do
  code="$(curl -fsS -o /dev/null -w '%{http_code}' "${URL[$w]}" || true)"
  echo "  $w -> $code"
  [ "$code" = "200" ] || rc=1
done
exit $rc
