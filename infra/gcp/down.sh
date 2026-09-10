#!/usr/bin/env bash
# Delete the demo VM and firewall rule. Run this at the end of every session.
set -euo pipefail
cd "$(dirname "$0")"
# shellcheck source=config.sh
. ./config.sh

MODE="${1:-delete}"   # delete | stop

case "$MODE" in
  stop)
    gcloud compute instances stop "$VM_NAME" --project "$GCP_PROJECT" --zone "$GCP_ZONE" || true
    echo "stopped $VM_NAME (disk still billed ~a few cents/day; run '$0 delete' to remove)"
    ;;
  delete)
    gcloud compute instances delete "$VM_NAME" --project "$GCP_PROJECT" --zone "$GCP_ZONE" -q || true
    gcloud compute firewall-rules delete "$FW_RULE" --project "$GCP_PROJECT" -q || true
    echo "deleted $VM_NAME and $FW_RULE"
    ;;
  *)
    echo "usage: $0 [delete|stop]" >&2; exit 2 ;;
esac
