#!/usr/bin/env bash
# Runs ON the demo VM. Idempotent. Turns on the WildFly statistics that the
# "App Server & JVM" dashboard needs, reloads, then lists which of the expected
# metric families actually appear on :9990/metrics.
#   ./scripts/on-vm-enable-statistics.sh
#
# Undertow (sessions, per-servlet/listener requests), every datasource pool, and
# transactions all default to statistics-enabled=false. The io worker thread
# metrics need nothing enabled.
set -euo pipefail

WFLY_HOME="${WFLY_HOME:-/opt/wildfly}"
cli() { sudo -u wildfly "$WFLY_HOME/bin/jboss-cli.sh" -c "$@"; }

cmds=(
  "/subsystem=undertow:write-attribute(name=statistics-enabled,value=true)"
  "/subsystem=transactions:write-attribute(name=statistics-enabled,value=true)"
)
for type in data-source xa-data-source; do
  names="$(cli --output-json "/subsystem=datasources:read-children-names(child-type=$type)" | jq -r '.result[]')"
  for ds in $names; do
    cmds+=("/subsystem=datasources/$type=$ds:write-attribute(name=statistics-enabled,value=true)")
  done
done

for c in "${cmds[@]}"; do
  echo "$c"
  cli "$c" >/dev/null
done
cli ":reload" >/dev/null

for i in $(seq 1 60); do
  [ "$(cli --output-json ':read-attribute(name=server-state)' 2>/dev/null | jq -r .result)" = "running" ] && break
  sleep 1
done

# The dashboard's metric families; "0" means the name needs fixing in the
# dashboard, or the thing it measures does not exist on this server.
m="$(curl -fsS http://localhost:9990/metrics)"
for fam in \
  wildfly_undertow_active_sessions \
  wildfly_undertow_request_count_total \
  wildfly_io_busy_task_thread_count \
  wildfly_io_core_pool_size \
  wildfly_io_max_pool_size \
  wildfly_io_queue_size \
  wildfly_datasources_pool_in_use_count \
  wildfly_datasources_pool_active_count \
  wildfly_datasources_pool_available_count; do
  printf '%-45s %s\n' "$fam" "$(grep -c "^${fam}{" <<<"$m" || true)"
done
