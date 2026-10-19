#!/usr/bin/env bash
set -euo pipefail

ACTION="${1:?ACTION is required: deploy|rollout|show}"
REGISTRY="${2:?REGISTRY is required}"
PROJECT="${3:?PROJECT is required}"
TAG="${4:?TAG is required}"
SERVICE="${5:-ALL}"
NAMESPACE="${6:-devops-test}"

case "${ACTION}" in
  deploy|rollout|show)
    ;;
  *)
    echo "Unsupported ACTION: ${ACTION}" >&2
    exit 2
    ;;
esac

case "${SERVICE}" in
  ALL|backend|order-worker|frontend)
    ;;
  *)
    echo "Unsupported SERVICE: ${SERVICE}" >&2
    exit 2
    ;;
esac

apply_backend_hpa() {
  local script_dir manifest available
  script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
  manifest="${BACKEND_HPA_FILE:-${script_dir}/80-backend-hpa.yaml}"
  if [[ -z "${BACKEND_HPA_FILE:-}" && ! -f "${manifest}" ]]; then
    manifest="${script_dir}/../../k8s/part7/80-backend-hpa.yaml"
  fi
  if [[ ! -f "${manifest}" ]]; then
    echo "Missing Backend HPA manifest: ${manifest}" >&2
    return 1
  fi
  if [[ ! "${NAMESPACE}" =~ ^[a-z0-9]([a-z0-9-]*[a-z0-9])?$ || ${#NAMESPACE} -gt 63 ]]; then
    echo "Invalid NAMESPACE: ${NAMESPACE}" >&2
    return 2
  fi

  if ! available="$(sudo kubectl --request-timeout=15s get apiservice v1beta1.metrics.k8s.io \
      -o jsonpath='{.status.conditions[?(@.type=="Available")].status}')"; then
    echo "Cannot read Metrics API. Check metrics-server before enabling HPA." >&2
    return 1
  fi
  if [[ "${available}" != "True" ]]; then
    echo "Metrics API is not ready. Check metrics-server before enabling HPA." >&2
    return 1
  fi

  # Jenkins 傳來的檔案與直接在專案內執行都可使用，並沿用部署的 namespace。
  sed "s/^  namespace: devops-test$/  namespace: ${NAMESPACE}/" "${manifest}" | \
    sudo kubectl --request-timeout=15s apply -n "${NAMESPACE}" -f -
}

deploy_backend() {
  apply_backend_hpa
  sudo kubectl set image \
    deployment/ticket-backend \
    "backend=${REGISTRY}/${PROJECT}/ticket-backend:${TAG}" \
    -n "${NAMESPACE}"
}

deploy_worker() {
  sudo kubectl set image \
    deployment/ticket-order-worker \
    "order-worker=${REGISTRY}/${PROJECT}/ticket-order-worker:${TAG}" \
    -n "${NAMESPACE}"
}

deploy_frontend() {
  sudo kubectl set image \
    deployment/ticket-frontend \
    "frontend=${REGISTRY}/${PROJECT}/ticket-frontend:${TAG}" \
    -n "${NAMESPACE}"
}

rollout_backend() {
  sudo kubectl rollout status \
    deployment/ticket-backend \
    -n "${NAMESPACE}" \
    --timeout=180s
}

rollout_worker() {
  sudo kubectl rollout status \
    deployment/ticket-order-worker \
    -n "${NAMESPACE}" \
    --timeout=180s
}

rollout_frontend() {
  sudo kubectl rollout status \
    deployment/ticket-frontend \
    -n "${NAMESPACE}" \
    --timeout=180s
}

show_image() {
  local deployment="$1"

  printf '%-24s ' "${deployment}"
  sudo kubectl get deployment "${deployment}" \
    -n "${NAMESPACE}" \
    -o jsonpath='{.spec.template.spec.containers[0].image}'
  echo
}

case "${ACTION}" in
  deploy)
    if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "backend" ]]; then
      deploy_backend
    fi

    if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "order-worker" ]]; then
      deploy_worker
    fi

    if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "frontend" ]]; then
      deploy_frontend
    fi
    ;;

  rollout)
    if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "backend" ]]; then
      rollout_backend
    fi

    if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "order-worker" ]]; then
      rollout_worker
    fi

    if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "frontend" ]]; then
      rollout_frontend
    fi
    ;;

  show)
    if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "backend" ]]; then
      show_image "ticket-backend"
      sudo kubectl --request-timeout=15s get hpa ticket-backend -n "${NAMESPACE}"
    fi

    if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "order-worker" ]]; then
      show_image "ticket-order-worker"
    fi

    if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "frontend" ]]; then
      show_image "ticket-frontend"
    fi
    ;;
esac
