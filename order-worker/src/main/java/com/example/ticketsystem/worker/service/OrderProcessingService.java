package com.example.ticketsystem.worker.service;

import com.example.ticketsystem.worker.model.TicketOrderMessage;
import com.example.ticketsystem.worker.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderProcessingService {

    private final OrderRepository orderRepository;

    public OrderProcessingService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @Transactional
    public ProcessResult process(TicketOrderMessage message) {
        if (orderRepository.existsByMessageId(message.messageId())) {
            return ProcessResult.ALREADY_PROCESSED;
        }

        if (orderRepository.existsByEventAndUser(
                message.eventId(),
                message.userId()
        )) {
            return ProcessResult.ORDER_ALREADY_EXISTS;
        }

        int affectedRows = orderRepository.decrementStockAtomic(message.eventId());

        if (affectedRows == 0) {
            throw new IllegalStateException(
                    "MySQL 已無庫存，eventId=" + message.eventId()
            );
        }

        orderRepository.insertOrder(
                message.eventId(),
                message.userId(),
                message.messageId()
        );

        return ProcessResult.CREATED;
    }

    public enum ProcessResult {
        CREATED,
        ALREADY_PROCESSED,
        ORDER_ALREADY_EXISTS
    }
}
