#!/usr/bin/env bash
# Runs ON the demo VM. Start/stop a STANDALONE jmx_exporter that reads WildFly's
# MBeans over remote JMX — the Spike E alternative to the MicroProfile Metrics
# subsystem (which is the default and needs none of this).
#
# Standalone (not a -javaagent): the 1.x javaagent conflicts with WildFly 26's
# boot (classloading + JBoss LogManager ordering). A separate process avoids
# both. It connects via the management port's JMX-over-remoting.
#
#   ./scripts/on-vm-jmx-exporter.sh on | off
set -euo pipefail

JMX_VERSION="${JMX_VERSION:-0.20.0}"
JMX_DIR=/opt/jmx-exporter
JAR="$JMX_DIR/jmx_prometheus_httpserver.jar"
CONF="$JMX_DIR/httpserver.yaml"
PORT=9404
JMX_USER="${JMX_USER:-jmxreader}"
JMX_PASS="${JMX_PASS:-jmxReader#2026}"

case "${1:-}" in
  on)
    sudo mkdir -p "$JMX_DIR"
    [ -s "$JAR" ] || sudo curl -fsSL -o "$JAR" \
      "https://repo1.maven.org/maven2/io/prometheus/jmx/jmx_prometheus_httpserver/${JMX_VERSION}/jmx_prometheus_httpserver-${JMX_VERSION}.jar"

    # A read-only management user for JMX (idempotent).
    sudo /opt/wildfly/bin/add-user.sh -s -u "$JMX_USER" -p "$JMX_PASS" -g monitor >/dev/null 2>&1 || true
    sudo chown -R wildfly:wildfly /opt/wildfly/standalone/configuration || true
    sudo systemctl restart wildfly
    for _ in $(seq 1 40); do
      [ "$(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/portal-web/login.xhtml || true)" = "200" ] && break
      sleep 1
    done

    sudo tee "$CONF" >/dev/null <<EOF
jmxUrl: service:jmx:remote+http://localhost:9990
username: $JMX_USER
password: $JMX_PASS
lowercaseOutputName: true
lowercaseOutputLabelNames: true
rules:
  - pattern: 'java.lang<type=Memory><(HeapMemoryUsage|NonHeapMemoryUsage)>(\w+)'
    name: jvm_memory_\$1_\$2_bytes
    type: GAUGE
  - pattern: 'java.lang<type=GarbageCollector, name=(.+)><>(CollectionCount|CollectionTime)'
    name: jvm_gc_\$2
    labels: { gc: "\$1" }
    type: COUNTER
  - pattern: 'java.lang<type=Threading><>(ThreadCount|DaemonThreadCount|PeakThreadCount)'
    name: jvm_threads_\$1
    type: GAUGE
  - pattern: 'java.lang<type=OperatingSystem><>(ProcessCpuLoad|SystemLoadAverage|OpenFileDescriptorCount)'
    name: process_\$1
    type: GAUGE
  - pattern: ".*"
EOF

    sudo pkill -f jmx_prometheus_httpserver 2>/dev/null || true
    sudo -b sh -c "exec java -jar '$JAR' $PORT '$CONF' >/var/log/jmx-exporter.log 2>&1"
    sleep 5
    ;;
  off)
    sudo pkill -f jmx_prometheus_httpserver 2>/dev/null || true
    ;;
  *)
    echo "usage: $0 on|off" >&2; exit 2 ;;
esac

code="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:${PORT}/metrics" || true)"
echo "jmx-exporter ${1}  ->  http://localhost:${PORT}/metrics = ${code}  (200 when on)"
