#!/usr/bin/env bash
set -euo pipefail

STOCK="${1:-1}"

sudo docker exec ticket-mysql \
  mysql -uticketuser -pticketpass ticketdb \
  -e "
    DELETE FROM ticket_order;
    ALTER TABLE ticket_order AUTO_INCREMENT = 1;

    UPDATE ticket_event
    SET total_stock = ${STOCK},
        stock = ${STOCK}
    WHERE id = 1;

    SELECT id,name,total_stock,stock
    FROM ticket_event
    WHERE id = 1;
  "
