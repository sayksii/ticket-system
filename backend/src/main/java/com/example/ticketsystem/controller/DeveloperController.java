package com.example.ticketsystem.controller;

import com.example.ticketsystem.service.DeveloperService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/developer")
@ConditionalOnProperty(name = "ticket.developer.enabled", havingValue = "true")
public class DeveloperController {
    private final DeveloperService service;
    private static final Set<String> ORIGINS = Set.of("http://ticket.local", "http://localhost:4173", "http://127.0.0.1:4173");

    public DeveloperController(DeveloperService service) { this.service = service; }

    @GetMapping("/capabilities")
    public Map<String, Object> capabilities() {
        return Map.of("enabled", true, "resetAvailable", service.resetAvailable(), "maxRequests", 500, "maxConcurrency", 10);
    }

    @GetMapping("/events/{eventId}/state")
    public DeveloperService.Snapshot state(@PathVariable long eventId, @RequestParam(defaultValue = "") String runPrefix) {
        return service.snapshot(eventId, runPrefix);
    }

    @PostMapping(value = "/reset", consumes = "application/json")
    public DeveloperService.Snapshot reset(@RequestBody ResetRequest body, HttpServletRequest request) {
        checkOrigin(request);
        if (!body.confirmed()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "請先確認刪除範圍");
        return service.reset(body.eventId(), body.stock(), body.expectedOrders());
    }

    private void checkOrigin(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        // 這不是登入/權限系統；僅避免其他網站在瀏覽器內跨來源操作。
        if (origin != null && !ORIGINS.contains(origin)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "請從本機或 ticket.local 操作");
    }

    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> problem(ResponseStatusException exception) {
        return org.springframework.http.ResponseEntity.status(exception.getStatusCode()).body(Map.of("message", exception.getReason()));
    }

    public record ResetRequest(long eventId, int stock, long expectedOrders, boolean confirmed) {}
}
