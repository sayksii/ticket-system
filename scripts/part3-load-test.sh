#!/usr/bin/env bash
set -euo pipefail

REQUESTS="${1:-50}"
PORTS_RAW="${2:-18080}"

if ! [[ "$REQUESTS" =~ ^[1-9][0-9]*$ ]]; then
  echo "用法：$0 [requests] [ports]"
  echo "單 backend：$0 50 18080"
  echo "雙 backend：$0 50 18080,18082"
  exit 1
fi

IFS=',' read -r -a PORTS <<< "$PORTS_RAW"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

PREFIX="part3-$(date +%s%N)-"

echo "Requests : $REQUESTS"
echo "Ports    : $PORTS_RAW"
echo

for i in $(seq 1 "$REQUESTS"); do
  INDEX=$(( (i - 1) % ${#PORTS[@]} ))
  PORT="${PORTS[$INDEX]}"

  curl -sS -X POST \
    "http://localhost:${PORT}/api/events/1/buy?userId=${PREFIX}${i}" \
    > "${TMP_DIR}/${i}.json" &
done

wait

SUCCESS=0
FAIL=0
CONFLICT=0

for file in "${TMP_DIR}"/*.json; do
  if grep -q '"success":true' "$file"; then
    SUCCESS=$((SUCCESS + 1))
  else
    FAIL=$((FAIL + 1))

    if grep -q '版本衝突' "$file"; then
      CONFLICT=$((CONFLICT + 1))
    fi
  fi
done

echo "HTTP 回應："
echo "success=true  : $SUCCESS"
echo "success=false : $FAIL"
echo "version conflict: $CONFLICT"
echo

echo "MySQL："
sudo docker exec ticket-mysql \
  mysql -uticketuser -pticketpass ticketdb \
  -e "
    SELECT
      e.total_stock,
      e.stock,
      e.version,
      COUNT(o.id) AS order_count,
      e.total_stock - COUNT(o.id) AS theoretical_remaining
    FROM ticket_event e
    LEFT JOIN ticket_order o
      ON o.event_id = e.id
    WHERE e.id = 1
    GROUP BY e.id, e.total_stock, e.stock, e.version;
  "
