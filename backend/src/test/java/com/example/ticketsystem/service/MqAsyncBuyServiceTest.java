package com.example.ticketsystem.service;

import com.example.ticketsystem.model.TicketOrderMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MqAsyncBuyServiceTest {
    @Test @SuppressWarnings({"unchecked", "rawtypes"}) void reservationSendsNormalOrderMessage() {
        var redis = mock(StringRedisTemplate.class); var producer = mock(RocketMqProducer.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(19L);
        var response = new MqAsyncBuyService(redis, producer).buy(1L, "user");
        var message = ArgumentCaptor.forClass(TicketOrderMessage.class);
        verify(producer).sendCreateOrder(message.capture());
        assertEquals(1L, message.getValue().eventId()); assertEquals("user", message.getValue().userId());
        assertNotNull(message.getValue().messageId());
        verify(redis).execute(any(RedisScript.class), eq(List.of("ticket:{1}:stock", "ticket:{1}:buyers", "ticket:{1}:status")), eq("user"));
        assertEquals("QUEUED", response.status()); assertEquals(19, response.remainingStock());
    }
    @Test @SuppressWarnings({"unchecked", "rawtypes"}) void refusalDoesNotSendMqMessage() {
        var redis = mock(StringRedisTemplate.class); var producer = mock(RocketMqProducer.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(-4L);
        assertFalse(new MqAsyncBuyService(redis, producer).buy(1L, "user").success());
        verifyNoInteractions(producer);
    }
    @Test @SuppressWarnings({"unchecked", "rawtypes"}) void failedSendRefundsStockAndBuyer() {
        var redis = mock(StringRedisTemplate.class); var producer = mock(RocketMqProducer.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(19L, 20L);
        doThrow(new IllegalStateException("offline")).when(producer).sendCreateOrder(any());
        assertEquals("MQ_SEND_FAILED", new MqAsyncBuyService(redis, producer).buy(1L, "user").status());
        verify(redis).execute(any(RedisScript.class), eq(List.of("ticket:{1}:stock", "ticket:{1}:buyers")), eq("user"));
    }
}
