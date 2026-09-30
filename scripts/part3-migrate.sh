#!/usr/bin/env bash
set -euo pipefail

echo "檢查 ticket_event.version 是否存在..."

EXISTS="$(
  sudo docker exec ticket-mysql \
    mysql -N -uticketuser -pticketpass ticketdb \
    -e "
      SELECT COUNT(*)
      FROM information_schema.COLUMNS
      WHERE TABLE_SCHEMA = 'ticketdb'
        AND TABLE_NAME = 'ticket_event'
        AND COLUMN_NAME = 'version';
    "
)"

if [ "$EXISTS" = "0" ]; then
  echo "version 不存在，新增欄位..."
  sudo docker exec ticket-mysql \
    mysql -uticketuser -pticketpass ticketdb \
    -e "
      ALTER TABLE ticket_event
      ADD COLUMN version INT NOT NULL DEFAULT 0
      AFTER stock;
    "
else
  echo "version 已存在，不重複新增。"
fi

sudo docker exec ticket-mysql \
  mysql -uticketuser -pticketpass ticketdb \
  -e "
    SHOW COLUMNS FROM ticket_event;
  "
