# Runbook — demo VM

How to bring the whole thing up on a fresh GCP VM, and the day-to-day commands.

## Prerequisites (operator workstation)

- `gcloud` CLI, authenticated, with a project that has billing enabled.
- The scripts read your current public IP and open SSH + Grafana to it only.
  If your IP changes, re-run `up.sh` (it updates the firewall rule).

## First-time setup

```bash
# 1. create the VM + firewall (e2-standard-4 in europe-west2)
./infra/gcp/up.sh

# 2. install JDK 17, Maven, Docker, k6, Chromium, WildFly 26.1; clone the repo
./infra/gcp/ssh.sh -- 'bash -s' < infra/gcp/bootstrap.sh

# 3. build + deploy the demo app to WildFly
./infra/gcp/ssh.sh -- 'cd ~/jboss-prometheus-grafana && ./scripts/on-vm-build-deploy.sh'

# 4. start Prometheus + Grafana
./infra/gcp/ssh.sh -- 'cd ~/jboss-prometheus-grafana && ./observability/stack.sh up'
```

Grafana: `http://<VM_EXTERNAL_IP>:3000` (admin / admin). The external IP is
printed by `up.sh`, or:

```bash
gcloud compute instances describe jpg-demo --zone europe-west2-a \
  --format='value(networkInterfaces[0].accessConfigs[0].natIP)'
```

## Day to day

| Task | Command (from the repo root on your workstation) |
|---|---|
| SSH in | `./infra/gcp/ssh.sh` |
| Rebuild + redeploy after a code change | `./infra/gcp/ssh.sh -- 'cd ~/jboss-prometheus-grafana && git pull && ./scripts/on-vm-build-deploy.sh'` |
| Run a labelled load test | `./infra/gcp/ssh.sh -- 'cd ~/jboss-prometheus-grafana && ./load/run-loadtest.sh --vus 3 --duration 120'` |
| Tail Prometheus / Grafana logs | `./infra/gcp/ssh.sh -- 'cd ~/jboss-prometheus-grafana && ./observability/stack.sh logs'` |
| Port-forward Prometheus to your machine | `./infra/gcp/ssh.sh -- -L 9090:localhost:9090` then open `http://localhost:9090` |
| **Stop the VM** (keeps disk, ~pennies/day) | `./infra/gcp/down.sh stop` |
| **Delete the VM** (end of session) | `./infra/gcp/down.sh` |

## Metrics toggle

WildFly starts with `-Dportal.metrics.enabled=false` (set in
`/etc/systemd/system/wildfly.service`). To turn instrumentation on for a load
test, edit that line to `true` and `sudo systemctl restart wildfly`. Phase 0 has
no backend yet, so this is a no-op until Phase 1.

## Cost

`e2-standard-4` in `europe-west2` is ~US$0.13/hour while running, plus a few
cents/day for the 30 GB disk. `down.sh` removes both. Nothing is billed once the
instance is deleted.
