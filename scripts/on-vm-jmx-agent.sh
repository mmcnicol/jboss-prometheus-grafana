#!/usr/bin/env bash
# Runs ON the demo VM. Enable/disable the jmx_exporter javaagent on WildFly —
# the Spike E ALTERNATIVE to the MicroProfile Metrics subsystem (which is the
# default and needs none of this).
#
#   ./scripts/on-vm-jmx-agent.sh on|off
set -euo pipefail
cd "$(dirname "$0")/.."

JMX_VERSION="${JMX_VERSION:-1.6.0}"
JMX_DIR=/opt/jmx-exporter
JMX_JAR="$JMX_DIR/jmx_prometheus_javaagent.jar"
JMX_CONF="$JMX_DIR/config.yaml"
PORT=9404
UNIT=/etc/systemd/system/wildfly.service
AGENT_OPT="-javaagent:${JMX_JAR}=${PORT}:${JMX_CONF}"

case "${1:-}" in
  on)
    sudo mkdir -p "$JMX_DIR"
    if [ ! -s "$JMX_JAR" ]; then
      sudo curl -fsSL -o "$JMX_JAR" \
        "https://github.com/prometheus/jmx_exporter/releases/download/${JMX_VERSION}/jmx_prometheus_javaagent-${JMX_VERSION}.jar"
    fi
    sudo cp observability/jmx-exporter/config.yaml "$JMX_CONF"
    sudo chmod -R a+rX "$JMX_DIR"
    if ! grep -q -- "-javaagent:${JMX_JAR}" "$UNIT"; then
      # Append the agent opt just before the closing quote of the JAVA_OPTS value.
      sudo sed -i -E "s#(Environment=\"JAVA_OPTS=[^\"]*)(\")#\\1 ${AGENT_OPT}\\2#" "$UNIT"
    fi
    ;;
  off)
    sudo sed -i -E "s# -javaagent:[^ \"]*jmx_prometheus_javaagent\\.jar=[0-9]+:[^ \"]*##" "$UNIT"
    ;;
  *)
    echo "usage: $0 on|off" >&2; exit 2 ;;
esac

sudo systemctl daemon-reload
sudo systemctl restart wildfly
for _ in $(seq 1 40); do
  [ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/portal-web/login.xhtml || true)" = "200" ] && break
  sleep 1
done
code="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${PORT}/metrics" || true)"
echo "jmx-agent ${1}  ->  http://localhost:${PORT}/metrics = ${code}  (200 when on)"
