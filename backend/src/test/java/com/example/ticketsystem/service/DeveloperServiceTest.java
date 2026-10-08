package com.example.ticketsystem.service;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeveloperServiceTest {
    private DeveloperService service;
    private JdbcTemplate db;
    private Map<String, String> values;
    private boolean failFinish;

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setup() {
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        db = new JdbcTemplate(source);
        db.execute("CREATE TABLE ticket_event(id BIGINT PRIMARY KEY, name VARCHAR(100), total_stock INT, stock INT, version INT)");
        db.execute("CREATE TABLE ticket_order(id BIGINT PRIMARY KEY, event_id BIGINT, user_id VARCHAR(100), status VARCHAR(30))");
        db.update("INSERT INTO ticket_event VALUES(1, 'test', 20, 18, 2), (2, 'other', 10, 9, 1)");
        db.update("INSERT INTO ticket_order VALUES(1,1,'demo-abcdef123456-1','SUCCESS'),(2,1,'normal-user','SUCCESS'),(3,2,'other-user','SUCCESS')");
        values = new HashMap<>(Map.of("ticket:{1}:stock", "18", "ticket:{1}:status", "OPEN", "ticket:{1}:buyers", "2"));
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        SetOperations<String, String> sets = mock(SetOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(redis.opsForSet()).thenReturn(sets);
        when(ops.get(anyString())).thenAnswer(i -> values.get(i.getArgument(0)));
        when(ops.multiGet(anyCollection())).thenAnswer(i -> ((Collection<String>) i.getArgument(0)).stream().map(values::get).toList());
        when(sets.size(anyString())).thenAnswer(i -> Long.valueOf(values.getOrDefault(i.getArgument(0), "0")));
        doAnswer(i -> { values.put(i.getArgument(0), i.getArgument(1)); return null; }).when(ops).set(anyString(), anyString());
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenAnswer(i -> {
            RedisScript script = i.getArgument(0);
            List<String> keys = i.getArgument(1);
            Object[] args = (Object[]) i.getRawArguments()[2];
            if (keys.size() == 1) {
                String old = values.getOrDefault(keys.getFirst(), "CLOSED");
                if ("RESETTING".equals(old)) return "BUSY";
                values.put(keys.getFirst(), "RESETTING"); return old;
            }
            if (keys.size() == 3) {
                if (failFinish) throw new IllegalStateException("test Redis interruption");
                values.put(keys.get(0), args[0].toString()); values.put(keys.get(1), "0");
                values.put(keys.get(2), "OPEN"); return 1L;
            }
            throw new AssertionError("Unexpected Redis operation");
        });
        service = new DeveloperService(source, redis, new DataSourceTransactionManager(source), "REDIS_LUA_MQ");
    }

    @Test void previewDoesNotDeleteAnything() {
        var result = service.snapshot(1, "");
        assertEquals(2, result.orderCount());
        assertEquals(3, db.queryForObject("SELECT COUNT(*) FROM ticket_order", Integer.class));
        assertEquals("OPEN", values.get("ticket:{1}:status"));
        assertEquals(3, values.size());
    }
    @Test void resetOnlySelectedEventWithoutExtraTablesOrKeys() {
        var result = service.reset(1, 30, 2);
        assertEquals(30, result.mysqlStock()); assertEquals(30, result.redisStock()); assertEquals(0, result.orderCount());
        assertEquals(30, result.totalStock()); assertEquals(0, result.buyers());
        assertEquals("OPEN", result.status()); assertEquals(3, values.size());
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM ticket_order WHERE event_id = 2", Integer.class));
        assertEquals(9, db.queryForObject("SELECT stock FROM ticket_event WHERE id = 2", Integer.class));
        assertEquals(0, db.queryForObject("SELECT version FROM ticket_event WHERE id = 1", Integer.class));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'PUBLIC'", Integer.class));
    }
    @Test void changedDataRequiresNewPreviewWithoutDeletion() {
        db.update("INSERT INTO ticket_order VALUES(4,1,'new-user','SUCCESS')");
        assertThrows(ResponseStatusException.class, () -> service.reset(1, 20, 2));
        assertEquals(4, db.queryForObject("SELECT COUNT(*) FROM ticket_order", Integer.class));
        assertEquals("OPEN", values.get("ticket:{1}:status"));
    }
    @Test void invalidInputsDoNotTouchData() {
        assertThrows(ResponseStatusException.class, () -> service.reset(1, 0, 2));
        assertThrows(ResponseStatusException.class, () -> service.reset(1, 1001, 2));
        assertThrows(ResponseStatusException.class, () -> service.reset(-1, 20, 2));
        assertThrows(ResponseStatusException.class, () -> service.reset(1, 20, -1));
        assertThrows(ResponseStatusException.class, () -> service.snapshot(1, "%"));
        assertThrows(ResponseStatusException.class, () -> service.reset(99, 20, 0));
        assertEquals(3, db.queryForObject("SELECT COUNT(*) FROM ticket_order", Integer.class));
    }
    @Test void concurrentResetIsRefused() {
        values.put("ticket:{1}:status", "RESETTING");
        assertThrows(ResponseStatusException.class, () -> service.reset(1, 20, 2));
        assertEquals(3, db.queryForObject("SELECT COUNT(*) FROM ticket_order", Integer.class));
        assertEquals("RESETTING", values.get("ticket:{1}:status"));
    }
    @Test void redisFailureAfterCommitLeavesEventClosed() {
        failFinish = true;
        assertThrows(ResponseStatusException.class, () -> service.reset(1, 20, 2));
        assertEquals("RESET_FAILED", values.get("ticket:{1}:status"));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM ticket_order WHERE event_id = 1", Integer.class));
        failFinish = false;
        assertEquals("OPEN", service.reset(1, 20, 0).status());
    }
    @Test void resultsCountOnlyThisRun() {
        var snapshot = service.snapshot(1, "demo-abcdef123456-");
        assertEquals(2, snapshot.orderCount()); assertEquals(1, snapshot.runOrders());
    }
    @Test void unfinishedOrdersMustSettleBeforeReset() {
        values.put("ticket:{1}:stock", "17"); values.put("ticket:{1}:buyers", "3");
        var error = assertThrows(ResponseStatusException.class, () -> service.reset(1, 20, 2));
        assertEquals("請等訂單處理完再重設", error.getReason());
        assertEquals(3, db.queryForObject("SELECT COUNT(*) FROM ticket_order", Integer.class));
        assertEquals("OPEN", values.get("ticket:{1}:status"));
    }
    @Test void missingRedisDataCanBeInitializedByConfirmedReset() {
        values.clear();
        var result = service.reset(1, 20, 2);
        assertEquals(20, result.redisStock()); assertEquals("OPEN", result.status());
    }
}
