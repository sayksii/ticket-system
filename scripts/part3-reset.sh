#!/usr/bin/env bash
set -euo pipefail

STOCK="${1:-20}"

if ! [[ "$STOCK" =~ ^[0-9]+$ ]]; then
  echo "用法：$0 [stock]"
  exit 1
fi

sudo docker exec ticket-mysql \
  mysql -uticketuser -pticketpass ticketdb \
  -e "
    DELETE FROM ticket_order;
    ALTER TABLE ticket_order AUTO_INCREMENT = 1;

    UPDATE ticket_event
    SET total_stock = ${STOCK},
        stock = ${STOCK},
        version = 0
    WHERE id = 1;

    SELECT id, name, total_stock, stock, version
    FROM ticket_event
    WHERE id = 1;
  "
