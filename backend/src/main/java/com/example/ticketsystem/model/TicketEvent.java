package com.example.ticketsystem.model;

public record TicketEvent(
        Long id,
        String name,
        Integer totalStock,
        Integer stock
) {
}
