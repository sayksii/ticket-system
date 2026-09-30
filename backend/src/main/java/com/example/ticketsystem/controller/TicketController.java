package com.example.ticketsystem.controller;

import com.example.ticketsystem.model.BuyResponse;
import com.example.ticketsystem.model.TicketEvent;
import com.example.ticketsystem.model.TicketOrder;
import com.example.ticketsystem.service.TicketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TicketController {

    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping("/events")
    public List<TicketEvent> getEvents() {
        return ticketService.getEvents();
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<?> getEvent(@PathVariable Long eventId) {
        try {
            return ResponseEntity.ok(ticketService.getEvent(eventId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/events/{eventId}/buy")
    public BuyResponse buy(
            @PathVariable Long eventId,
            @RequestParam String userId
    ) {
        return ticketService.buy(eventId, userId);
    }

    @GetMapping("/orders")
    public List<TicketOrder> getOrders(@RequestParam String userId) {
        return ticketService.getOrders(userId);
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
