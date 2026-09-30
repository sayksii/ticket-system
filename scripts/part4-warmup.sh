#!/usr/bin/env bash
set -euo pipefail

EVENT_ID="${1:-1}"

if ! [[ "$EVENT_ID" =~ ^[1-9][0-9]*$ ]]; then
  echo "用法：$0 [eventId]"
  exit 1
fi

ROW="$({
  sudo docker exec ticket-mysql \
    mysql -N -B -uticketuser -pticketpass ticketdb \
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
  echo "學習壓測請先執行：./scripts/part3-reset.sh <stock>"
  exit 1
fi

STOCK_KEY="ticket:{${EVENT_ID}}:stock"
BUYERS_KEY="ticket:{${EVENT_ID}}:buyers"
STATUS_KEY="ticket:{${EVENT_ID}}:status"

sudo docker exec ticket-redis redis-cli --raw EVAL "
  redis.call('SET', KEYS[1], ARGV[1])
  redis.call('DEL', KEYS[2])
  redis.call('SET', KEYS[3], 'OPEN')
  return 1
" 3 "$STOCK_KEY" "$BUYERS_KEY" "$STATUS_KEY" "$STOCK" >/dev/null

echo "Warmup 完成：eventId=${EVENT_ID}"
echo "stock=${STOCK}"
echo "buyers=0"
echo "status=OPEN"
