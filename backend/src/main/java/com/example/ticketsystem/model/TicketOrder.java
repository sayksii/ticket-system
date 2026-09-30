package com.example.ticketsystem.model;

import java.time.LocalDateTime;

public record TicketOrder(
        Long id,
        Long eventId,
        String eventName,
        String userId,
        String status,
        LocalDateTime createdAt
) {
}
