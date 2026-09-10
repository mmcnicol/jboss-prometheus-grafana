#!/usr/bin/env bash
# Runs ON the demo VM (Debian 12). Idempotent. Safe to re-run.
#   ./infra/gcp/ssh.sh -- 'bash -s' < infra/gcp/bootstrap.sh
set -euo pipefail

WILDFLY_VERSION="${WILDFLY_VERSION:-26.1.3.Final}"
REPO_URL="${REPO_URL:-https://github.com/mmcnicol/jboss-prometheus-grafana.git}"
REPO_DIR="${REPO_DIR:-$HOME/jboss-prometheus-grafana}"
WFLY_HOME="/opt/wildfly"

log() { echo -e "\n=== $* ==="; }

log "apt base packages"
sudo apt-get update -qq
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq \
  curl ca-certificates gnupg unzip git jq \
  openjdk-17-jdk maven \
  chromium chromium-driver

log "docker"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sudo sh
fi
sudo usermod -aG docker "$USER" || true
sudo systemctl enable --now docker

log "k6"
if ! command -v k6 >/dev/null 2>&1; then
  k6_tag="$(curl -fsSL https://api.github.com/repos/grafana/k6/releases/latest | jq -r .tag_name)"
  curl -fsSL "https://github.com/grafana/k6/releases/download/${k6_tag}/k6-${k6_tag}-linux-amd64.tar.gz" \
    | sudo tar xz -C /usr/local/bin --strip-components=1 --wildcards '*/k6'
fi

log "wildfly $WILDFLY_VERSION"
if [ ! -x "${WFLY_HOME}-${WILDFLY_VERSION}/bin/standalone.sh" ]; then
  sudo rm -rf "${WFLY_HOME}-${WILDFLY_VERSION}"
  tmp="$(mktemp -d)"
  curl -fsSL -o "$tmp/wildfly.zip" \
    "https://github.com/wildfly/wildfly/releases/download/${WILDFLY_VERSION}/wildfly-${WILDFLY_VERSION}.zip"
  # zip's top-level dir is already "wildfly-<version>"
  sudo unzip -q "$tmp/wildfly.zip" -d /opt
  rm -rf "$tmp"
fi
sudo ln -sfn "${WFLY_HOME}-${WILDFLY_VERSION}" "$WFLY_HOME"
id wildfly >/dev/null 2>&1 || sudo useradd -r -d "$WFLY_HOME" -s /sbin/nologin wildfly
sudo chown -R wildfly:wildfly "${WFLY_HOME}-${WILDFLY_VERSION}"

log "wildfly systemd unit"
sudo tee /etc/systemd/system/wildfly.service >/dev/null <<UNIT
[Unit]
Description=WildFly ${WILDFLY_VERSION} (jpg-demo)
After=network.target

[Service]
User=wildfly
Group=wildfly
Environment=JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
# Metrics OFF by default; the load-test workflow sets it true (see docs FR2).
Environment="JAVA_OPTS=-Xms512m -Xmx1536m -Dportal.metrics.enabled=false"
ExecStart=${WFLY_HOME}/bin/standalone.sh -b 0.0.0.0
Restart=on-failure
RestartSec=5
LimitNOFILE=65536

[Install]
WantedBy=multi-user.target
UNIT
sudo systemctl daemon-reload
sudo systemctl enable wildfly
sudo systemctl restart wildfly

log "repo"
if [ ! -d "$REPO_DIR/.git" ]; then
  git clone "$REPO_URL" "$REPO_DIR"
else
  git -C "$REPO_DIR" pull --ff-only || true
fi

log "versions"
java -version
mvn -v | head -1
docker --version
docker compose version | head -1
k6 version
chromium --version || chromium-browser --version || true
chromedriver --version || true
systemctl is-active wildfly && echo "wildfly: $(curl -fsS -o /dev/null -w '%{http_code}' http://localhost:8080/ || true) on :8080"

log "done"
echo "next: cd $REPO_DIR && docker compose -f observability/docker-compose.yml up -d"
