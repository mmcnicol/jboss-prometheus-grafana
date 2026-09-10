#!/usr/bin/env bash
# Create (or start) the demo VM and its firewall rule. Idempotent.
set -euo pipefail
cd "$(dirname "$0")"
# shellcheck source=config.sh
. ./config.sh

echo "project=$GCP_PROJECT zone=$GCP_ZONE vm=$VM_NAME admin_cidr=${ADMIN_CIDR:-<unset>}"
[ -n "${ADMIN_CIDR:-}" ] || { echo "ADMIN_CIDR unset; aborting"; exit 1; }

gcloud services enable compute.googleapis.com --project "$GCP_PROJECT" -q

# --- firewall: SSH + Grafana from your IP only -------------------------------
if gcloud compute firewall-rules describe "$FW_RULE" --project "$GCP_PROJECT" >/dev/null 2>&1; then
  gcloud compute firewall-rules update "$FW_RULE" --project "$GCP_PROJECT" \
    --source-ranges "$ADMIN_CIDR" --allow "$ADMIN_PORTS"
else
  gcloud compute firewall-rules create "$FW_RULE" --project "$GCP_PROJECT" \
    --direction INGRESS --action ALLOW --rules "$ADMIN_PORTS" \
    --source-ranges "$ADMIN_CIDR" --target-tags "$VM_TAG" \
    --description "jboss-prometheus-grafana demo: admin access from operator IP"
fi

# --- instance ---------------------------------------------------------------
if gcloud compute instances describe "$VM_NAME" --project "$GCP_PROJECT" --zone "$GCP_ZONE" >/dev/null 2>&1; then
  state="$(gcloud compute instances describe "$VM_NAME" --project "$GCP_PROJECT" --zone "$GCP_ZONE" --format='value(status)')"
  [ "$state" = "RUNNING" ] || gcloud compute instances start "$VM_NAME" --project "$GCP_PROJECT" --zone "$GCP_ZONE"
else
  gcloud compute instances create "$VM_NAME" \
    --project "$GCP_PROJECT" --zone "$GCP_ZONE" \
    --machine-type "$VM_MACHINE_TYPE" \
    --image-family "$VM_IMAGE_FAMILY" --image-project "$VM_IMAGE_PROJECT" \
    --boot-disk-size "$VM_DISK_SIZE" --boot-disk-type "$VM_DISK_TYPE" \
    --tags "$VM_TAG" \
    --labels "purpose=jpg-demo,managed-by=repo" \
    --metadata enable-oslogin=FALSE
fi

ip="$(gcloud compute instances describe "$VM_NAME" --project "$GCP_PROJECT" --zone "$GCP_ZONE" \
  --format='value(networkInterfaces[0].accessConfigs[0].natIP)')"
echo
echo "VM ready. external IP: $ip"
echo "  ssh:      ./infra/gcp/ssh.sh"
echo "  bootstrap: ./infra/gcp/ssh.sh -- 'bash -s' < infra/gcp/bootstrap.sh"
echo "  grafana:  http://$ip:3000  (after bootstrap + stack up)"
