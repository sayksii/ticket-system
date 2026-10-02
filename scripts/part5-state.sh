#!/usr/bin/env bash
set -euo pipefail

EVENT_ID="${1:-1}"

if ! [[ "$EVENT_ID" =~ ^[1-9][0-9]*$ ]]; then
  echo "用法：$0 [eventId]"
  exit 1
fi

STOCK_KEY="ticket:{${EVENT_ID}}:stock"
BUYERS_KEY="ticket:{${EVENT_ID}}:buyers"
STATUS_KEY="ticket:{${EVENT_ID}}:status"

REDIS_STOCK="$(sudo docker exec ticket-redis redis-cli --raw GET "$STOCK_KEY")"
REDIS_STATUS="$(sudo docker exec ticket-redis redis-cli --raw GET "$STATUS_KEY")"
REDIS_BUYERS="$(sudo docker exec ticket-redis redis-cli --raw SCARD "$BUYERS_KEY")"

[ -n "$REDIS_STOCK" ] || REDIS_STOCK="<not-warmed>"
[ -n "$REDIS_STATUS" ] || REDIS_STATUS="<not-warmed>"

echo "Redis："
echo "status = ${REDIS_STATUS}"
echo "stock  = ${REDIS_STOCK}"
echo "buyers = ${REDIS_BUYERS}"
echo

echo "MySQL："
sudo docker exec ticket-mysql \
  mysql -uticketuser -pticketpass ticketdb \
  -e "
    SELECT
      e.id,
      e.total_stock,
      e.stock,
      e.version,
      COUNT(o.id) AS order_count,
      SUM(CASE WHEN o.message_id IS NOT NULL THEN 1 ELSE 0 END) AS mq_order_count
    FROM ticket_event e
    LEFT JOIN ticket_order o ON o.event_id = e.id
    WHERE e.id = ${EVENT_ID}
    GROUP BY e.id, e.total_stock, e.stock, e.version;

    SELECT id,event_id,user_id,message_id,status,created_at
    FROM ticket_order
    WHERE event_id = ${EVENT_ID}
    ORDER BY id DESC
    LIMIT 10;
  "
