package com.example.ticketsystem.service;

import com.example.ticketsystem.model.BuyResponse;
import com.example.ticketsystem.model.TicketEvent;
import com.example.ticketsystem.model.TicketOrder;
import com.example.ticketsystem.repository.TicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UnsafeBuyService unsafeBuyService;
    private final AtomicBuyService atomicBuyService;
    private final OptimisticBuyService optimisticBuyService;
    private final PessimisticBuyService pessimisticBuyService;
    private final SynchronizedBuyService synchronizedBuyService;
    private final DistributedLockBuyService distributedLockBuyService;
    private final RedisLuaBuyService redisLuaBuyService;

    @Value("${ticket.buy-mode:ATOMIC}")
    private String buyMode;

    public TicketService(
            TicketRepository ticketRepository,
            UnsafeBuyService unsafeBuyService,
            AtomicBuyService atomicBuyService,
            OptimisticBuyService optimisticBuyService,
            PessimisticBuyService pessimisticBuyService,
            SynchronizedBuyService synchronizedBuyService,
            DistributedLockBuyService distributedLockBuyService,
            RedisLuaBuyService redisLuaBuyService
    ) {
        this.ticketRepository = ticketRepository;
        this.unsafeBuyService = unsafeBuyService;
        this.atomicBuyService = atomicBuyService;
        this.optimisticBuyService = optimisticBuyService;
        this.pessimisticBuyService = pessimisticBuyService;
        this.synchronizedBuyService = synchronizedBuyService;
        this.distributedLockBuyService = distributedLockBuyService;
        this.redisLuaBuyService = redisLuaBuyService;
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

    public String getBuyMode() {
        return buyMode;
    }

    public BuyResponse buy(Long eventId, String userId) {
        if (userId == null || userId.isBlank()) {
            return new BuyResponse(false, "userId 不可為空", null, null);
        }

        String mode = buyMode.trim().toUpperCase(Locale.ROOT);

        return switch (mode) {
            case "UNSAFE" -> unsafeBuyService.buy(eventId, userId);
            case "ATOMIC" -> atomicBuyService.buy(eventId, userId);
            case "OPTIMISTIC" -> optimisticBuyService.buy(eventId, userId);
            case "PESSIMISTIC" -> pessimisticBuyService.buy(eventId, userId);
            case "SYNCHRONIZED" -> synchronizedBuyService.buy(eventId, userId);
            case "DISTRIBUTED_LOCK" -> distributedLockBuyService.buy(eventId, userId);
            case "REDIS_LUA" -> redisLuaBuyService.buy(eventId, userId);
            default -> throw new IllegalArgumentException(
                    "未知 BUY_MODE：" + buyMode
            );
        };
    }
}
