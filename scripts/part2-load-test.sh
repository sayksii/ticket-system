#!/usr/bin/env bash
set -euo pipefail

REQUESTS="${1:-20}"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

PREFIX="load-$(date +%s%N)-"

for i in $(seq 1 "$REQUESTS"); do
  curl -sS -X POST \
    "http://localhost:18080/api/events/1/buy?userId=${PREFIX}${i}" \
    > "${TMP_DIR}/${i}.json" &
done

wait

SUCCESS=0
FAIL=0

for file in "${TMP_DIR}"/*.json; do
  if grep -q '"success":true' "$file"; then
    SUCCESS=$((SUCCESS + 1))
  else
    FAIL=$((FAIL + 1))
  fi
done

echo "success=true  : ${SUCCESS}"
echo "success=false : ${FAIL}"

sudo docker exec ticket-mysql \
  mysql -uticketuser -pticketpass ticketdb \
  -e "
    SELECT
      e.total_stock,
      e.stock,
      COUNT(o.id) AS order_count,
      e.total_stock - COUNT(o.id) AS theoretical_remaining
    FROM ticket_event e
    LEFT JOIN ticket_order o
      ON o.event_id = e.id
    WHERE e.id = 1
    GROUP BY e.id, e.total_stock, e.stock;
  "
