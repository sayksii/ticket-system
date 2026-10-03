#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-devops-test}"
MYSQL_POD="${MYSQL_POD:-ticket-mysql-0}"
REDIS_POD="${REDIS_POD:-ticket-redis-0}"
EVENT_ID="${1:-1}"

if ! [[ "$EVENT_ID" =~ ^[1-9][0-9]*$ ]]; then
  echo "用法：$0 [eventId]"
  exit 1
fi

DB_USER="$(sudo kubectl get secret ticket-db-secret -n "$NAMESPACE" -o jsonpath='{.data.MYSQL_USER}' | base64 -d)"
DB_PASSWORD="$(sudo kubectl get secret ticket-db-secret -n "$NAMESPACE" -o jsonpath='{.data.MYSQL_PASSWORD}' | base64 -d)"
DB_NAME="$(sudo kubectl get secret ticket-db-secret -n "$NAMESPACE" -o jsonpath='{.data.MYSQL_DATABASE}' | base64 -d)"

ROW="$({
  sudo kubectl exec -n "$NAMESPACE" "$MYSQL_POD" -- \
    mysql -N -B -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" \
    -e "
      SELECT e.stock, COUNT(o.id)
      FROM ticket_event e
      LEFT JOIN ticket_order o ON o.event_id = e.id
      WHERE e.id = ${EVENT_ID}
      GROUP BY e.id, e.stock;
    "
} 2>/dev/null)"

if [ -z "$ROW" ]; then
  echo "找不到 eventId=${EVENT_ID}"
  exit 1
fi

read -r STOCK ORDER_COUNT <<< "$ROW"

if [ "$ORDER_COUNT" != "0" ]; then
  echo "目前 DB 已有 ${ORDER_COUNT} 筆訂單，不自動清空 Redis buyers。"
  echo "學習壓測請先執行：./part7-reset.sh <stock> ${EVENT_ID}"
  exit 1
fi

STOCK_KEY="ticket:{${EVENT_ID}}:stock"
BUYERS_KEY="ticket:{${EVENT_ID}}:buyers"
STATUS_KEY="ticket:{${EVENT_ID}}:status"

LUA="
  redis.call('SET', KEYS[1], ARGV[1])
  redis.call('DEL', KEYS[2])
  redis.call('SET', KEYS[3], 'OPEN')
  return 1
"

sudo kubectl exec -n "$NAMESPACE" "$REDIS_POD" -- \
  redis-cli --raw EVAL "$LUA" 3 \
  "$STOCK_KEY" "$BUYERS_KEY" "$STATUS_KEY" "$STOCK" >/dev/null

echo "Warmup 完成：eventId=${EVENT_ID}"
echo "stock=${STOCK}"
echo "buyers=0"
echo "status=OPEN"
