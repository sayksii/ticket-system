package com.example.ticketsystem.service;

import com.example.ticketsystem.model.BuyResponse;
import com.example.ticketsystem.model.TicketEvent;
import com.example.ticketsystem.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OptimisticBuyService {

    private final TicketRepository ticketRepository;
    private final BuySupport buySupport;

    public OptimisticBuyService(
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

        buySupport.delay();

        int affectedRows = ticketRepository.updateStockOptimistic(
                eventId,
                event.stock() - 1,
                event.version()
        );

        if (affectedRows == 0) {
            return new BuyResponse(
                    false,
                    "版本衝突：別的 Request 已先修改資料，請重試",
                    null,
                    null
            );
        }

        Long orderId = ticketRepository.insertOrder(eventId, userId);

        TicketEvent latest = ticketRepository.findEventById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("活動不存在"));

        return new BuyResponse(
                true,
                "搶票成功（Optimistic Lock）",
                orderId,
                latest.stock()
        );
    }
}
