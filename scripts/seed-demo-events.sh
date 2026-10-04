#!/usr/bin/env bash
# 只新增缺少的示範活動；不重設任何既有庫存、訂單或 Redis buyers。
set -euo pipefail

NAMESPACE="${NAMESPACE:-devops-test}"
MYSQL_POD="${MYSQL_POD:-ticket-mysql-0}"
REDIS_POD="${REDIS_POD:-ticket-redis-0}"
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
SEED_SQL="${SEED_SQL:-$SCRIPT_DIR/../backend/src/main/resources/data.sql}"
KUBECTL=(sudo kubectl --request-timeout=15s)

if [[ ! -f "$SEED_SQL" ]]; then
  echo "找不到初始化 SQL：$SEED_SQL" >&2
  exit 1
fi

# 使用 Pod 原有的環境變數，不把 Secret 或密碼印到終端機。
mysql_query() {
  "${KUBECTL[@]}" exec -i -n "$NAMESPACE" "$MYSQL_POD" -- sh -c \
    'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql --batch --skip-column-names --user="$MYSQL_USER" "$MYSQL_DATABASE"'
}

echo "新增尚未存在的活動（不修改已存在的活動）…"
mysql_query < "$SEED_SQL"

ROWS="$(mysql_query <<'SQL'
SELECT e.id, e.stock, COUNT(o.id), e.name
FROM ticket_event e
LEFT JOIN ticket_order o ON o.event_id = e.id
WHERE e.name IN (
  'Kubernetes DevOps Concert', 'Cloud Native Summit', 'Spring Boot Workshop',
  'Indie Music Night', 'Future Design Expo', 'Weekend Jazz Market'
)
GROUP BY e.id, e.stock, e.name
ORDER BY e.id;
SQL
)"

# 必須三個 key 全部不存在才初始化，原有狀態一律跳過。沒有 DEL。
# 同一個活動的 key 使用同一個 Redis hash tag，Lua 原子操作。
LUA="
  if redis.call('EXISTS', KEYS[1], KEYS[2], KEYS[3]) > 0 then
    return 0
  end
  redis.call('SET', KEYS[1], ARGV[1])
  redis.call('SET', KEYS[3], 'OPEN')
  return 1
"
while IFS=$'\t' read -r EVENT_ID STOCK ORDER_COUNT EVENT_NAME; do
  [[ -n "$EVENT_ID" ]] || continue
  if ! [[ "$EVENT_ID" =~ ^[1-9][0-9]*$ && "$STOCK" =~ ^[0-9]+$ && "$ORDER_COUNT" =~ ^[0-9]+$ ]]; then
    echo "活動資料格式異常，中止。" >&2
    exit 1
  fi
  if [[ "$ORDER_COUNT" != "0" ]]; then
    echo "保留：$EVENT_NAME（ID=$EVENT_ID，已有 $ORDER_COUNT 筆訂單）"
    continue
  fi
  RESULT="$("${KUBECTL[@]}" exec -n "$NAMESPACE" "$REDIS_POD" -- redis-cli --raw EVAL "$LUA" 3 \
    "ticket:{${EVENT_ID}}:stock" "ticket:{${EVENT_ID}}:buyers" "ticket:{${EVENT_ID}}:status" "$STOCK")"
  case "$RESULT" in
    1) echo "初始化：$EVENT_NAME（ID=$EVENT_ID，stock=$STOCK，OPEN）" ;;
    0) echo "保留：$EVENT_NAME（ID=$EVENT_ID，Redis 已存在，不覆寫）" ;;
    *) echo "Redis 初始化失敗：$EVENT_NAME，回應=$RESULT" >&2; exit 1 ;;
  esac
done <<< "$ROWS"

echo "完成。此操作沒有刪除資料，也不會重置既有活動。"
