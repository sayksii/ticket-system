package com.example.ticketsystem.model;

public record BuyResponse(
        boolean success,
        String message,
        Long orderId,
        Integer remainingStock,
        String status,
        String messageId
) {
    public BuyResponse(
            boolean success,
            String message,
            Long orderId,
            Integer remainingStock
    ) {
        this(success, message, orderId, remainingStock, null, null);
    }
}
