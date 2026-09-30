# ticket-system-part1

PART 1：最小可跑搶票系統。

## 啟動

```bash
docker compose up --build -d
docker compose ps
```

Browser：

```text
http://<HOST_IP>:18081
```

Backend health：

```text
http://<HOST_IP>:18080/api/health
```

## 停止

```bash
docker compose down
```

若連 MySQL Volume 一起刪：

```bash
docker compose down -v
```

## 注意

PART 1 的 `buy()` 故意使用「先查庫存，再更新」。
單人操作可以工作，但高併發可能發生 Race Condition。
PART 2 會故意放大這個問題並壓測。
