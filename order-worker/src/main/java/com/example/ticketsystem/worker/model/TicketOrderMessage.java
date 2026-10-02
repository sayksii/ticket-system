package com.example.ticketsystem.worker.model;

public record TicketOrderMessage(
        String messageId,
        Long eventId,
        String userId,
        String createdAt
) {
}
