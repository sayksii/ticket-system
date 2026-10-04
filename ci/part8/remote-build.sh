#!/usr/bin/env bash
set -euo pipefail

REGISTRY="${1:?REGISTRY is required}"
PROJECT="${2:?PROJECT is required}"
TAG="${3:?TAG is required}"
SERVICE="${4:-ALL}"

case "${SERVICE}" in
  ALL|backend|order-worker|frontend)
    ;;
  *)
    echo "Unsupported SERVICE: ${SERVICE}" >&2
    exit 2
    ;;
esac

build_and_push() {
  local service="$1"
  local context="$2"
  local image="${REGISTRY}/${PROJECT}/${service}:${TAG}"

  echo
  echo "============================================================"
  echo "BUILD: ${image}"
  echo "CONTEXT: ${context}"
  echo "============================================================"

  sudo docker build \
    -t "${image}" \
    "${context}"

  echo
  echo "PUSH: ${image}"

  sudo docker push "${image}"
}

if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "backend" ]]; then
  build_and_push "ticket-backend" "./backend"
fi

if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "order-worker" ]]; then
  build_and_push "ticket-order-worker" "./order-worker"
fi

if [[ "${SERVICE}" == "ALL" || "${SERVICE}" == "frontend" ]]; then
  build_and_push "ticket-frontend" "./frontend"
fi

echo
echo "Remote Docker Build / Push completed."
