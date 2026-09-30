package com.example.ticketsystem.service;

import com.example.ticketsystem.model.BuyResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

@Service
public class DistributedLockBuyService {

    private static final Duration LOCK_TTL = Duration.ofSeconds(5);
    private static final long WAIT_TIMEOUT_MS = 10000;
    private static final long RETRY_INTERVAL_MS = 20;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT =
            new DefaultRedisScript<>(
                    """
                    if redis.call('get', KEYS[1]) == ARGV[1] then
                        return redis.call('del', KEYS[1])
                    end
                    return 0
                    """,
                    Long.class
            );

    private final StringRedisTemplate redisTemplate;
    private final UnsafeBuyService unsafeBuyService;

    public DistributedLockBuyService(
            StringRedisTemplate redisTemplate,
            UnsafeBuyService unsafeBuyService
    ) {
        this.redisTemplate = redisTemplate;
        this.unsafeBuyService = unsafeBuyService;
    }

    public BuyResponse buy(Long eventId, String userId) {
        String lockKey = "lock:ticket:event:" + eventId;
        String token = UUID.randomUUID().toString();

        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MS;
        boolean locked = false;

        while (System.currentTimeMillis() < deadline) {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, token, LOCK_TTL);

            if (Boolean.TRUE.equals(acquired)) {
                locked = true;
                break;
            }

            sleep(RETRY_INTERVAL_MS);
        }

        if (!locked) {
            return new BuyResponse(
                    false,
                    "系統忙碌：取得 Distributed Lock 逾時",
                    null,
                    null
            );
        }

        try {
            // Lock 取得後，才讓這個 Request 進入原本危險的 read -> write 流程。
            return unsafeBuyService.buy(eventId, userId);
        } finally {
            // 只有 token 相同的人才可以刪除自己的 lock。
            redisTemplate.execute(
                    UNLOCK_SCRIPT,
                    Collections.singletonList(lockKey),
                    token
            );
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
