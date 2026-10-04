#!/usr/bin/env bash
set -euo pipefail

SERVICE="${1:-ALL}"
NAMESPACE="${2:-devops-test}"

# 在 k3s-server 上執行，直接走 Traefik / Ingress 正式路徑。
INGRESS_URL="${INGRESS_URL:-http://10.10.10.10}"
HOST_HEADER="${HOST_HEADER:-ticket.local}"

case "${SERVICE}" in
  ALL|backend|order-worker|frontend)
    ;;
  *)
    echo "Unsupported SERVICE: ${SERVICE}" >&2
    exit 2
    ;;
esac

if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "backend" ]]; then
  echo "Smoke Test: Backend /api/health"

  health="$(
    curl -fsS \
      -H "Host: ${HOST_HEADER}" \
      "${INGRESS_URL}/api/health"
  )"

  echo "${health}"

  printf '%s' "${health}" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'
fi

if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "frontend" ]]; then
  echo
  echo "Smoke Test: Frontend /"

  curl -fsS \
    -H "Host: ${HOST_HEADER}" \
    "${INGRESS_URL}/" \
    >/tmp/part8-frontend-smoke.html

  test -s /tmp/part8-frontend-smoke.html
  rm -f /tmp/part8-frontend-smoke.html

  echo "Frontend HTTP response: OK"
fi

if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "order-worker" ]]; then
  echo
  echo "Smoke Test: Order Worker Ready Replicas"

  desired="$(
    sudo kubectl get deployment ticket-order-worker \
      -n "${NAMESPACE}" \
      -o jsonpath='{.spec.replicas}'
  )"

  ready="$(
    sudo kubectl get deployment ticket-order-worker \
      -n "${NAMESPACE}" \
      -o jsonpath='{.status.readyReplicas}'
  )"

  ready="${ready:-0}"

  echo "desired=${desired} ready=${ready}"

  [[ "${ready}" == "${desired}" ]]
fi

echo
echo "PART 8 smoke test passed."
