#!/usr/bin/env bash
set -euo pipefail

echo "檢查 ticket_order.message_id 是否存在..."

COLUMN_EXISTS="$(
  sudo docker exec ticket-mysql \
    mysql -N -uticketuser -pticketpass ticketdb \
    -e "
      SELECT COUNT(*)
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = 'ticketdb'
        AND TABLE_NAME = 'ticket_order'
        AND COLUMN_NAME = 'message_id';
    "
)"

if [ "$COLUMN_EXISTS" = "0" ]; then
  echo "message_id 不存在，新增欄位..."
  sudo docker exec ticket-mysql \
    mysql -uticketuser -pticketpass ticketdb \
    -e "
      ALTER TABLE ticket_order
      ADD COLUMN message_id VARCHAR(64) NULL
      AFTER user_id;
    "
else
  echo "message_id 已存在，不重複新增。"
fi

echo "檢查 message_id UNIQUE INDEX 是否存在..."

INDEX_EXISTS="$(
  sudo docker exec ticket-mysql \
    mysql -N -uticketuser -pticketpass ticketdb \
    -e "
      SELECT COUNT(*)
      FROM information_schema.STATISTICS
      WHERE TABLE_SCHEMA = 'ticketdb'
        AND TABLE_NAME = 'ticket_order'
        AND INDEX_NAME = 'uk_ticket_order_message';
    "
)"

if [ "$INDEX_EXISTS" = "0" ]; then
  echo "UNIQUE INDEX 不存在，建立 uk_ticket_order_message..."
  sudo docker exec ticket-mysql \
    mysql -uticketuser -pticketpass ticketdb \
    -e "
      ALTER TABLE ticket_order
      ADD CONSTRAINT uk_ticket_order_message
      UNIQUE (message_id);
    "
else
  echo "uk_ticket_order_message 已存在，不重複建立。"
fi

echo
echo "目前 ticket_order 結構："
sudo docker exec ticket-mysql \
  mysql -uticketuser -pticketpass ticketdb \
  -e "SHOW COLUMNS FROM ticket_order; SHOW INDEX FROM ticket_order;"
