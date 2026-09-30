package com.example.ticketsystem.service;

import com.example.ticketsystem.model.BuyResponse;
import com.example.ticketsystem.model.TicketEvent;
import com.example.ticketsystem.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AtomicBuyService {

    private final TicketRepository ticketRepository;

    public AtomicBuyService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Transactional
    public BuyResponse buy(Long eventId, String userId) {
        if (ticketRepository.existsOrder(eventId, userId)) {
            return new BuyResponse(
                    false,
                    "同一使用者不能重複搶同一活動",
                    null,
                    null
            );
        }

        int affectedRows = ticketRepository.decrementStockAtomic(eventId);

        if (affectedRows == 0) {
            return new BuyResponse(false, "票已售完", null, 0);
        }

        Long orderId = ticketRepository.insertOrder(eventId, userId);

        TicketEvent event = ticketRepository.findEventById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("活動不存在"));

        return new BuyResponse(
                true,
                "搶票成功（DB Atomic UPDATE）",
                orderId,
                event.stock()
        );
    }
}
