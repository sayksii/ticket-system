package com.example.ticketsystem.model;

public record TicketOrderMessage(
        String messageId,
        Long eventId,
        String userId,
        String createdAt
) {
}
