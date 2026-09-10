#!/usr/bin/env bash
# Thin wrapper around `gcloud compute ssh` for the demo VM.
#   ./infra/gcp/ssh.sh                       # interactive shell
#   ./infra/gcp/ssh.sh -- 'uname -a'         # run a command
#   ./infra/gcp/ssh.sh -- -L 9090:localhost:9090   # port-forward Prometheus
set -euo pipefail
cd "$(dirname "$0")"
# shellcheck source=config.sh
. ./config.sh
exec gcloud compute ssh "$VM_NAME" --project "$GCP_PROJECT" --zone "$GCP_ZONE" "$@"
