package com.example.ticketsystem.service;

import com.example.ticketsystem.model.BuyResponse;
import com.example.ticketsystem.model.TicketEvent;
import com.example.ticketsystem.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PessimisticBuyService {

    private final TicketRepository ticketRepository;
    private final BuySupport buySupport;

    public PessimisticBuyService(
            TicketRepository ticketRepository,
            BuySupport buySupport
    ) {
        this.ticketRepository = ticketRepository;
        this.buySupport = buySupport;
    }

    @Transactional
    public BuyResponse buy(Long eventId, String userId) {
        // FOR UPDATE 必須在 Transaction 中才有意義。
        TicketEvent event = ticketRepository.findEventByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("活動不存在"));

        if (ticketRepository.existsOrder(eventId, userId)) {
            return new BuyResponse(
                    false,
                    "同一使用者不能重複搶同一活動",
                    null,
                    null
            );
        }

        if (event.stock() <= 0) {
            return new BuyResponse(false, "票已售完", null, 0);
        }

        // 故意停一下，方便觀察其他 Transaction 等待 row lock。
        buySupport.delay();

        int newStock = event.stock() - 1;

        ticketRepository.setStockUnsafe(eventId, newStock);

        Long orderId = ticketRepository.insertOrder(eventId, userId);

        return new BuyResponse(
                true,
                "搶票成功（Pessimistic Lock）",
                orderId,
                newStock
        );
    }
}
