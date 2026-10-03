#!/usr/bin/env bash
set -euo pipefail

NAMESPACE="${NAMESPACE:-devops-test}"
MYSQL_POD="${MYSQL_POD:-ticket-mysql-0}"
STOCK="${1:-20}"
EVENT_ID="${2:-1}"

if ! [[ "$STOCK" =~ ^[0-9]+$ ]]; then
  echo "用法：$0 [stock] [eventId]"
  exit 1
fi

if ! [[ "$EVENT_ID" =~ ^[1-9][0-9]*$ ]]; then
  echo "用法：$0 [stock] [eventId]"
  exit 1
fi

DB_USER="$(sudo kubectl get secret ticket-db-secret -n "$NAMESPACE" -o jsonpath='{.data.MYSQL_USER}' | base64 -d)"
DB_PASSWORD="$(sudo kubectl get secret ticket-db-secret -n "$NAMESPACE" -o jsonpath='{.data.MYSQL_PASSWORD}' | base64 -d)"
DB_NAME="$(sudo kubectl get secret ticket-db-secret -n "$NAMESPACE" -o jsonpath='{.data.MYSQL_DATABASE}' | base64 -d)"

sudo kubectl exec -n "$NAMESPACE" "$MYSQL_POD" -- \
  mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" \
  -e "
    DELETE FROM ticket_order;
    ALTER TABLE ticket_order AUTO_INCREMENT = 1;

    UPDATE ticket_event
    SET total_stock = ${STOCK},
        stock = ${STOCK},
        version = 0
    WHERE id = ${EVENT_ID};

    SELECT id, name, total_stock, stock, version
    FROM ticket_event
    WHERE id = ${EVENT_ID};
  "
