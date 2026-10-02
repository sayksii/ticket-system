package com.example.ticketsystem.service;

import com.example.ticketsystem.model.BuyResponse;
import com.example.ticketsystem.model.TicketOrderMessage;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MqAsyncBuyService {

    private static final long RESULT_SOLD_OUT = -1L;
    private static final long RESULT_DUPLICATE = -2L;
    private static final long RESULT_NOT_WARMED = -3L;
    private static final long RESULT_NOT_OPEN = -4L;

    private static final DefaultRedisScript<Long> BUY_SCRIPT =
            loadScript("lua/ticket-buy.lua");

    private static final DefaultRedisScript<Long> COMPENSATE_SCRIPT =
            loadScript("lua/ticket-compensate.lua");

    private final StringRedisTemplate redisTemplate;
    private final RocketMqProducer rocketMqProducer;

    public MqAsyncBuyService(
            StringRedisTemplate redisTemplate,
            RocketMqProducer rocketMqProducer
    ) {
        this.redisTemplate = redisTemplate;
        this.rocketMqProducer = rocketMqProducer;
    }

    public BuyResponse buy(Long eventId, String userId) {
        Long luaResult = redisTemplate.execute(
                BUY_SCRIPT,
                List.of(stockKey(eventId), buyersKey(eventId), statusKey(eventId)),
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

        String messageId = UUID.randomUUID().toString();
        TicketOrderMessage message = new TicketOrderMessage(
                messageId,
                eventId,
                userId,
                Instant.now().toString()
        );

        try {
            rocketMqProducer.sendCreateOrder(message);
        } catch (RuntimeException e) {
            compensate(eventId, userId);
            return new BuyResponse(
                    false,
                    "取得資格，但 RocketMQ 送出失敗；已退回 Redis 資格",
                    null,
                    null,
                    "MQ_SEND_FAILED",
                    messageId
            );
        }

        return new BuyResponse(
                true,
                "已取得資格，訂單排隊處理中",
                null,
                luaResult.intValue(),
                "QUEUED",
                messageId
        );
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
