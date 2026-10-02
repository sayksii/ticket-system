package com.example.ticketsystem.worker.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public OrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByMessageId(String messageId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_order WHERE message_id = ?",
                Integer.class,
                messageId
        );
        return count != null && count > 0;
    }

    public boolean existsByEventAndUser(Long eventId, String userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_order WHERE event_id = ? AND user_id = ?",
                Integer.class,
                eventId,
                userId
        );
        return count != null && count > 0;
    }

    public int decrementStockAtomic(Long eventId) {
        return jdbcTemplate.update(
                "UPDATE ticket_event " +
                "SET stock = stock - 1, version = version + 1 " +
                "WHERE id = ? AND stock > 0",
                eventId
        );
    }

    public int insertOrder(
            Long eventId,
            String userId,
            String messageId
    ) {
        return jdbcTemplate.update(
                "INSERT INTO ticket_order(event_id, user_id, message_id, status) " +
                "VALUES (?, ?, ?, 'SUCCESS')",
                eventId,
                userId,
                messageId
        );
    }
}
