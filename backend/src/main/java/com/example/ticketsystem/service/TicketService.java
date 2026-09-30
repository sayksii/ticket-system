package com.example.ticketsystem.service;

import com.example.ticketsystem.model.BuyResponse;
import com.example.ticketsystem.model.TicketEvent;
import com.example.ticketsystem.model.TicketOrder;
import com.example.ticketsystem.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;

    @Value("${demo.delay-ms:0}")
    private long delayMs;

    public TicketService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    public List<TicketEvent> getEvents() {
        return ticketRepository.findAllEvents();
    }

    public TicketEvent getEvent(Long eventId) {
        return ticketRepository.findEventById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("活動不存在"));
    }

    public List<TicketOrder> getOrders(String userId) {
        return ticketRepository.findOrdersByUserId(userId);
    }

    @Transactional
    public BuyResponse buy(Long eventId, String userId) {
        if (userId == null || userId.isBlank()) {
            return new BuyResponse(false, "userId 不可為空", null, null);
        }

        if (ticketRepository.existsOrder(eventId, userId)) {
            return new BuyResponse(false, "同一使用者不能重複搶同一活動", null, null);
        }

        TicketEvent event = getEvent(eventId);

        if (event.stock() <= 0) {
            return new BuyResponse(false, "票已售完", null, 0);
        }

        // PART 1 故意保留先查庫存、再計算、再更新的寫法。
        // 單人操作可用，但高併發可能出現 Race Condition。
        sleepIfNeeded();

        int newStock = event.stock() - 1;
        ticketRepository.setStock(eventId, newStock);

        try {
            Long orderId = ticketRepository.insertOrder(eventId, userId);
            return new BuyResponse(true, "搶票成功", orderId, newStock);
        } catch (DuplicateKeyException e) {
            return new BuyResponse(false, "同一使用者不能重複搶同一活動", null, null);
        }
    }

    private void sleepIfNeeded() {
        if (delayMs <= 0) {
            return;
        }

        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
