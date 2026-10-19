#!/usr/bin/env bash
set -euo pipefail

TASK_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
RELEASE_SCRIPT="${TASK_ROOT}/ci/part8/k8s-release.sh"
HPA_MANIFEST="${TASK_ROOT}/k8s/part7/80-backend-hpa.yaml"

bash -n "${RELEASE_SCRIPT}"
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
grep -q 'k8s/part7/80-backend-hpa.yaml' "${TASK_ROOT}/Jenkinsfile"
grep -q 'bash scripts/hpa.test.sh' "${TASK_ROOT}/Jenkinsfile"
echo "PASS: HPA configuration and Jenkins transfer"

# 所有 kubectl 呼叫都由假指令接手，不連叢集、不改真實資源。
sudo() {
  [[ "$1" == kubectl ]] || return 90
  shift
  printf 'CALL: %s\n' "$*" >&2
  case " $* " in
    *" get apiservice "*)
      case "${TASK_HPA_MODE}" in
        metrics_error) return 1 ;;
        metrics_unavailable) printf 'False' ;;
        metrics_unknown) ;;
        *) printf 'True' ;;
      esac
      ;;
    *" apply "*)
      local payload
      payload="$(cat)"
      [[ "${payload}" == *"namespace: ${TASK_HPA_NAMESPACE}"* ]] || return 91
      [[ "${payload}" == *"kind: HorizontalPodAutoscaler"* ]] || return 92
      [[ "${TASK_HPA_MODE}" != apply_error ]] || return 1
      echo "HPA_APPLIED"
      ;;
    *" set image "*) echo "IMAGE_UPDATED" ;;
    *" rollout status "*) echo "ROLLOUT_READY" ;;
    *" get deployment "*) echo "ticket-backend:test" ;;
    *" get hpa "*) echo "ticket-backend 10%/70% 2 4 2" ;;
    *) return 93 ;;
  esac
}
export -f sudo

check_case() {
  local label="$1" action="$2" service="$3" mode="$4" expected="$5" namespace="${6:-devops-test}"
  local actual result
  set +e
  result="$(TASK_HPA_MODE="${mode}" TASK_HPA_NAMESPACE="${namespace}" \
    bash "${RELEASE_SCRIPT}" "${action}" registry project test "${service}" "${namespace}" 2>&1)"
  actual=$?
  set -e
  if [[ "${actual}" != "${expected}" ]]; then
    printf 'FAIL: %s expected=%s actual=%s\n%s\n' "${label}" "${expected}" "${actual}" "${result}"
    exit 1
  fi
  case "${label}" in
    backend|all|custom_namespace)
      [[ "${result}" == *HPA_APPLIED*IMAGE_UPDATED* ]] || return 1
      ;;
    frontend|worker|rollout)
      [[ "${result}" != *HPA_APPLIED* && "${result}" != *apiservice* ]] || return 1
      ;;
    show)
      [[ "${result}" == *"get hpa ticket-backend"* ]] || return 1
      ;;
    *) [[ "${result}" != *IMAGE_UPDATED* ]] || return 1 ;;
  esac
  [[ "${result}" != *" scale "* && "${result}" != *" delete "* ]] || return 1
  echo "PASS: ${label}"
}

check_case backend deploy backend success 0
check_case all deploy ALL success 0
check_case custom_namespace deploy backend success 0 staging
check_case frontend deploy frontend success 0
check_case worker deploy order-worker success 0
check_case rollout rollout backend success 0
check_case show show backend success 0
check_case metrics_error deploy backend metrics_error 1
check_case metrics_unavailable deploy backend metrics_unavailable 1
check_case metrics_unknown deploy backend metrics_unknown 1
check_case apply_error deploy backend apply_error 1
check_case invalid_namespace deploy backend success 2 Bad/Namespace
BACKEND_HPA_FILE="${TASK_ROOT}/k8s/part7/not-found.yaml" \
  check_case missing_manifest deploy backend success 1

echo "HPA checks passed."
