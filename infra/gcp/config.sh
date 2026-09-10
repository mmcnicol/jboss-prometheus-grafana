# Shared configuration for the GCP VM scripts. Source this; don't run it.
# Override any value by exporting it before calling up.sh / down.sh.

: "${GCP_PROJECT:=$(gcloud config get-value project 2>/dev/null)}"
: "${GCP_ZONE:=europe-west2-a}"          # London
: "${GCP_REGION:=${GCP_ZONE%-*}}"
: "${VM_NAME:=jpg-demo}"
: "${VM_MACHINE_TYPE:=e2-standard-4}"    # 4 vCPU / 16 GB
: "${VM_IMAGE_FAMILY:=debian-12}"
: "${VM_IMAGE_PROJECT:=debian-cloud}"
: "${VM_DISK_SIZE:=30GB}"
: "${VM_DISK_TYPE:=pd-balanced}"
: "${VM_TAG:=jpg-demo}"
: "${FW_RULE:=jpg-demo-allow-admin}"

# Ports opened to your workstation only (SSH + Grafana). Prometheus/collector
# stay internal; reach them with:  ./infra/gcp/ssh.sh -- -L 9090:localhost:9090
: "${ADMIN_PORTS:=tcp:22,tcp:3000}"

# Your current public IP, /32. Auto-detected unless you export ADMIN_CIDR.
if [ -z "${ADMIN_CIDR:-}" ]; then
  _ip="$(curl -fsS --max-time 10 https://ifconfig.me 2>/dev/null || true)"
  [ -z "$_ip" ] && _ip="$(curl -fsS --max-time 10 https://api.ipify.org 2>/dev/null || true)"
  if [ -n "$_ip" ]; then
    ADMIN_CIDR="${_ip}/32"
  else
    echo "config.sh: could not auto-detect your public IP; export ADMIN_CIDR=x.x.x.x/32" >&2
  fi
fi

export GCP_PROJECT GCP_ZONE GCP_REGION VM_NAME VM_MACHINE_TYPE VM_IMAGE_FAMILY \
  VM_IMAGE_PROJECT VM_DISK_SIZE VM_DISK_TYPE VM_TAG FW_RULE ADMIN_PORTS ADMIN_CIDR
