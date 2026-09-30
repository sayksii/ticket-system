package com.example.ticketsystem.service;

import com.example.ticketsystem.model.BuyResponse;
import com.example.ticketsystem.model.TicketEvent;
import com.example.ticketsystem.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnsafeBuyService {

    private final TicketRepository ticketRepository;
    private final BuySupport buySupport;

    public UnsafeBuyService(
            TicketRepository ticketRepository,
            BuySupport buySupport
    ) {
        this.ticketRepository = ticketRepository;
        this.buySupport = buySupport;
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

        TicketEvent event = ticketRepository.findEventById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("活動不存在"));

        if (event.stock() <= 0) {
            return new BuyResponse(false, "票已售完", null, 0);
        }

        // 故意保留 PART 2 的 read -> calculate -> write。
        buySupport.delay();

        int newStock = event.stock() - 1;

        ticketRepository.setStockUnsafe(eventId, newStock);

        Long orderId = ticketRepository.insertOrder(eventId, userId);

        return new BuyResponse(
                true,
                "搶票成功",
                orderId,
                newStock
        );
    }
}
