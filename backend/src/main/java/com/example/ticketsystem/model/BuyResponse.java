package com.example.ticketsystem.model;

public record BuyResponse(
        boolean success,
        String message,
        Long orderId,
        Integer remainingStock
) {
}
