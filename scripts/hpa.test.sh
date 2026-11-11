#!/usr/bin/env bash
set -euo pipefail

TASK_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
HPA_MANIFEST="${TASK_ROOT}/k8s/part7/80-backend-hpa.yaml"

grep -q '^apiVersion: autoscaling/v2$' "${HPA_MANIFEST}"
grep -q '^kind: HorizontalPodAutoscaler$' "${HPA_MANIFEST}"
grep -q '^  minReplicas: 2$' "${HPA_MANIFEST}"
grep -q '^  maxReplicas: 4$' "${HPA_MANIFEST}"
grep -q '^          averageUtilization: 70$' "${HPA_MANIFEST}"
if grep -q '^  replicas:' "${TASK_ROOT}/k8s/part7/40-backend.yaml"; then
  echo "Backend replicas must be managed by HPA." >&2
  exit 1
fi
grep -q '^              cpu: 100m$' "${TASK_ROOT}/k8s/part7/40-backend.yaml"

echo "HPA checks passed."
