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

# 容許部署切換時的暫時性錯誤，持續失敗仍以非零狀態結束。
ingress_get() {
  curl -fsS \
    --connect-timeout 3 \
    --max-time 10 \
    --retry 5 \
    --retry-delay 2 \
    --retry-max-time 70 \
    --retry-connrefused \
    -H "Host: ${HOST_HEADER}" \
    "${INGRESS_URL}$1"
}

if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "backend" ]]; then
  echo "Smoke Test: Backend /api/health"

  health="$(ingress_get /api/health)"

  echo "${health}"

  printf '%s' "${health}" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'
fi

if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "frontend" ]]; then
  echo
  echo "Smoke Test: Frontend /"

  frontend="$(ingress_get /)"
  test -n "${frontend}"

  echo "Frontend HTTP response: OK"
fi

if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "order-worker" ]]; then
  echo
  echo "Smoke Test: Order Worker Ready Replicas"

  desired="$(
    sudo kubectl --request-timeout=10s get deployment ticket-order-worker \
      -n "${NAMESPACE}" \
      -o jsonpath='{.spec.replicas}'
  )"

  ready="$(
    sudo kubectl --request-timeout=10s get deployment ticket-order-worker \
      -n "${NAMESPACE}" \
      -o jsonpath='{.status.readyReplicas}'
  )"

  ready="${ready:-0}"

  echo "desired=${desired} ready=${ready}"

  [[ "${ready}" == "${desired}" ]]
fi

echo
echo "PART 8 smoke test passed."
