package com.example.ticketsystem.service;

import com.example.ticketsystem.model.BuyResponse;
import com.example.ticketsystem.model.TicketEvent;
import com.example.ticketsystem.repository.TicketRepository;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

@Service
public class RedisLuaBuyService {

    private static final long RESULT_SOLD_OUT = -1L;
    private static final long RESULT_DUPLICATE = -2L;
    private static final long RESULT_NOT_WARMED = -3L;
    private static final long RESULT_NOT_OPEN = -4L;

    private static final DefaultRedisScript<Long> BUY_SCRIPT =
            loadScript("lua/ticket-buy.lua");

    private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT =
            loadScript("lua/ticket-compensate.lua");

    private final StringRedisTemplate redisTemplate;
    private final TicketRepository ticketRepository;
    private final TransactionTemplate transactionTemplate;

    public RedisLuaBuyService(
            StringRedisTemplate redisTemplate,
            TicketRepository ticketRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.redisTemplate = redisTemplate;
        this.ticketRepository = ticketRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public BuyResponse buy(Long eventId, String userId) {
        String stockKey = stockKey(eventId);
        String buyersKey = buyersKey(eventId);
        String statusKey = statusKey(eventId);

        Long luaResult = redisTemplate.execute(
                BUY_SCRIPT,
                List.of(stockKey, buyersKey, statusKey),
                userId
        );

        if (luaResult == null) {
            throw new IllegalStateException("Redis Lua 沒有回傳結果");
        }

        if (luaResult == RESULT_NOT_OPEN) {
            return new BuyResponse(false, "活動尚未開放或已結束", null, null);
        }

        if (luaResult == RESULT_NOT_WARMED) {
            return new BuyResponse(false, "Redis 尚未預熱", null, null);
        }

        if (luaResult == RESULT_DUPLICATE) {
            return new BuyResponse(
                    false,
                    "同一使用者不能重複搶同一活動",
                    null,
                    null
            );
        }

        if (luaResult == RESULT_SOLD_OUT) {
            return new BuyResponse(false, "票已售完", null, 0);
        }

        try {
            BuyResponse response = transactionTemplate.execute(status -> {
                int affectedRows = ticketRepository.decrementStockAtomic(eventId);

                if (affectedRows == 0) {
                    throw new IllegalStateException(
                            "Redis 有資格，但 MySQL 已無庫存；需要補償 Redis"
                    );
                }

                Long orderId = ticketRepository.insertOrder(eventId, userId);

                TicketEvent event = ticketRepository.findEventById(eventId)
                        .orElseThrow(() -> new IllegalArgumentException("活動不存在"));

                return new BuyResponse(
                        true,
                        "搶票成功（Redis Lua + DB Atomic UPDATE）",
                        orderId,
                        event.stock()
                );
            });

            if (response == null) {
                throw new IllegalStateException("MySQL Transaction 沒有回傳結果");
            }

            return response;
        } catch (DuplicateKeyException e) {
            compensate(eventId, userId);
            return new BuyResponse(
                    false,
                    "同一使用者不能重複搶同一活動",
                    null,
                    null
            );
        } catch (RuntimeException e) {
            compensate(eventId, userId);
            throw e;
        }
    }

    private void compensate(Long eventId, String userId) {
        redisTemplate.execute(
                COMPENSATE_SCRIPT,
                List.of(stockKey(eventId), buyersKey(eventId)),
                userId
        );
    }

    private static DefaultRedisScript<Long> loadScript(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(
                new ResourceScriptSource(new ClassPathResource(path))
        );
        script.setResultType(Long.class);
        return script;
    }

    private String stockKey(Long eventId) {
        return "ticket:{" + eventId + "}:stock";
    }

    private String buyersKey(Long eventId) {
        return "ticket:{" + eventId + "}:buyers";
    }

    private String statusKey(Long eventId) {
        return "ticket:{" + eventId + "}:status";
    }
}
