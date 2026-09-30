package com.example.ticketsystem.repository;

import com.example.ticketsystem.model.TicketEvent;
import com.example.ticketsystem.model.TicketOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Repository
public class TicketRepository {

    private final JdbcTemplate jdbcTemplate;

    public TicketRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TicketEvent> findAllEvents() {
        return jdbcTemplate.query(
                "SELECT id, name, total_stock, stock FROM ticket_event ORDER BY id",
                (rs, rowNum) -> new TicketEvent(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getInt("total_stock"),
                        rs.getInt("stock")
                )
        );
    }

    public Optional<TicketEvent> findEventById(Long eventId) {
        List<TicketEvent> result = jdbcTemplate.query(
                "SELECT id, name, total_stock, stock FROM ticket_event WHERE id = ?",
                (rs, rowNum) -> new TicketEvent(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getInt("total_stock"),
                        rs.getInt("stock")
                ),
                eventId
        );
        return result.stream().findFirst();
    }

    public boolean existsOrder(Long eventId, String userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ticket_order WHERE event_id = ? AND user_id = ?",
                Integer.class,
                eventId,
                userId
        );
        return count != null && count > 0;
    }

    public void setStock(Long eventId, Integer newStock) {
        jdbcTemplate.update(
                "UPDATE ticket_event SET stock = ? WHERE id = ?",
                newStock,
                eventId
        );
    }

    public Long insertOrder(Long eventId, String userId) {
        jdbcTemplate.update(
                "INSERT INTO ticket_order(event_id, user_id, status) VALUES (?, ?, 'SUCCESS')",
                eventId,
                userId
        );

        return jdbcTemplate.queryForObject(
                "SELECT id FROM ticket_order WHERE event_id = ? AND user_id = ?",
                Long.class,
                eventId,
                userId
        );
    }

    public List<TicketOrder> findOrdersByUserId(String userId) {
        return jdbcTemplate.query(
                "SELECT o.id, o.event_id, e.name AS event_name, o.user_id, o.status, o.created_at " +
                "FROM ticket_order o " +
                "JOIN ticket_event e ON e.id = o.event_id " +
                "WHERE o.user_id = ? " +
                "ORDER BY o.id DESC",
                (rs, rowNum) -> {
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    return new TicketOrder(
                            rs.getLong("id"),
                            rs.getLong("event_id"),
                            rs.getString("event_name"),
                            rs.getString("user_id"),
                            rs.getString("status"),
                            createdAt.toLocalDateTime()
                    );
                },
                userId
        );
    }
}
