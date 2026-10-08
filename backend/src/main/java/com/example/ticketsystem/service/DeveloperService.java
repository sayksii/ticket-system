package com.example.ticketsystem.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(name = "ticket.developer.enabled", havingValue = "true")
public class DeveloperService {
    private final JdbcTemplate db;
    private final StringRedisTemplate redis;
    private final TransactionTemplate transaction;
    private final String buyMode;

    private static final DefaultRedisScript<String> CLOSE = new DefaultRedisScript<>(
            "local old=redis.call('GET',KEYS[1]) or 'CLOSED'; if old == 'RESETTING' then return 'BUSY' end; " +
            "redis.call('SET',KEYS[1],'RESETTING'); return old", String.class);
    private static final DefaultRedisScript<Long> FINISH = new DefaultRedisScript<>(
            "redis.call('SET',KEYS[1],ARGV[1]); redis.call('DEL',KEYS[2]); " +
            "redis.call('SET',KEYS[3],'OPEN'); return 1", Long.class);

    public DeveloperService(DataSource dataSource, StringRedisTemplate redis,
                            PlatformTransactionManager manager, @Value("${ticket.buy-mode:ATOMIC}") String buyMode) {
        this.db = new JdbcTemplate(dataSource);
        this.db.setQueryTimeout(5);
        this.redis = redis;
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setTimeout(8);
        this.buyMode = buyMode;
    }

    public boolean resetAvailable() { return "REDIS_LUA_MQ".equalsIgnoreCase(buyMode.trim()); }

    public Snapshot snapshot(long eventId, String runPrefix) {
        checkEvent(eventId);
        if (runPrefix == null || (!runPrefix.isEmpty() && !runPrefix.matches("demo-[a-f0-9]{12}-"))) {
            throw problem(HttpStatus.BAD_REQUEST, "測試識別碼不正確");
        }
        List<Snapshot> rows = db.query("SELECT id, name, total_stock, stock FROM ticket_event WHERE id = ?",
                (rs, n) -> new Snapshot(rs.getLong("id"), rs.getString("name"), rs.getInt("total_stock"), rs.getInt("stock"),
                        null, 0, "", 0, 0, Instant.now().toString()), eventId);
        if (rows.isEmpty()) throw problem(HttpStatus.NOT_FOUND, "找不到活動");
        Snapshot event = rows.getFirst();
        String base = base(eventId);
        List<String> values = redis.opsForValue().multiGet(List.of(base + "stock", base + "status"));
        Long buyers = redis.opsForSet().size(base + "buyers");
        long orders = countOrders(eventId);
        long runOrders = runPrefix.isEmpty() ? 0 : db.queryForObject(
                "SELECT COUNT(*) FROM ticket_order WHERE event_id = ? AND user_id LIKE ? AND status = 'SUCCESS'", Long.class, eventId, runPrefix + "%");
        return new Snapshot(event.eventId(), event.name(), event.totalStock(), event.mysqlStock(),
                values.get(0) == null ? null : Integer.valueOf(values.get(0)), buyers == null ? 0 : buyers,
                values.get(1) == null ? "未預熱" : values.get(1), orders, runOrders, Instant.now().toString());
    }

    public Snapshot reset(long eventId, int stock, long expectedOrders) {
        requireMq();
        checkEvent(eventId);
        if (stock < 1 || stock > 1000) throw problem(HttpStatus.BAD_REQUEST, "庫存請輸入 1～1000");
        if (expectedOrders < 0) throw problem(HttpStatus.BAD_REQUEST, "訂單筆數不正確");
        snapshot(eventId, "");
        String base = base(eventId);
        String previous = redis.execute(CLOSE, List.of(base + "status"));
        if (previous == null || "BUSY".equals(previous)) throw problem(HttpStatus.CONFLICT, "此活動已有重設進行中");
        AtomicBoolean changed = new AtomicBoolean(false);
        try {
            transaction.executeWithoutResult(tx -> {
                db.queryForObject("SELECT id FROM ticket_event WHERE id = ? FOR UPDATE", Long.class, eventId);
                Snapshot current = snapshot(eventId, "");
                if (current.orderCount() != expectedOrders) {
                    throw problem(HttpStatus.CONFLICT, "資料已變動，尚未刪除，請重新預覽");
                }
                if (!"RESET_FAILED".equals(previous) && current.redisStock() != null
                        && (current.redisStock() != current.mysqlStock() || current.buyers() != current.orderCount())) {
                    throw problem(HttpStatus.CONFLICT, "請等訂單處理完再重設");
                }
                changed.set(true);
                db.update("DELETE FROM ticket_order WHERE event_id = ?", eventId);
                db.update("UPDATE ticket_event SET total_stock = ?, stock = ?, version = 0 WHERE id = ?",
                        stock, stock, eventId);
            });
            Long finished = redis.execute(FINISH, List.of(base + "stock", base + "buyers", base + "status"), Integer.toString(stock));
            if (!Long.valueOf(1).equals(finished)) throw new IllegalStateException("Redis 重設失敗");
        } catch (RuntimeException e) {
            try { redis.opsForValue().set(base + "status", changed.get() ? "RESET_FAILED" : previous); }
            catch (RuntimeException ignored) { /* Redis 中斷時保留 RESETTING，不自動解鎖。 */ }
            if (e instanceof ResponseStatusException response) throw response;
            throw problem(HttpStatus.SERVICE_UNAVAILABLE, "重設失敗，活動保持關閉，請重新查看資料");
        }
        return snapshot(eventId, "");
    }

    private long countOrders(long eventId) {
        return db.queryForObject("SELECT COUNT(*) FROM ticket_order WHERE event_id = ?", Long.class, eventId);
    }
    private void requireMq() {
        if (!resetAvailable()) throw problem(HttpStatus.CONFLICT, "重設工具僅支援 REDIS_LUA_MQ 模式");
    }
    private static void checkEvent(long eventId) {
        if (eventId < 1) throw problem(HttpStatus.BAD_REQUEST, "活動編號不正確");
    }
    private static String base(long id) { return "ticket:{" + id + "}:"; }
    private static ResponseStatusException problem(HttpStatus status, String message) { return new ResponseStatusException(status, message); }

    public record Snapshot(long eventId, String name, int totalStock, int mysqlStock, Integer redisStock, long buyers,
                           String status, long orderCount, long runOrders, String observedAt) {}
}
