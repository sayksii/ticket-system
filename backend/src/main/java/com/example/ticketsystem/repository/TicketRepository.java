package com.example.ticketsystem.repository;

import com.example.ticketsystem.model.TicketEvent;
import com.example.ticketsystem.model.TicketOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
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
                "SELECT id, name, total_stock, stock, version FROM ticket_event ORDER BY id",
                (rs, rowNum) -> new TicketEvent(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getInt("total_stock"),
                        rs.getInt("stock"),
                        rs.getInt("version")
                )
        );
    }

    public Optional<TicketEvent> findEventById(Long eventId) {
        List<TicketEvent> result = jdbcTemplate.query(
                "SELECT id, name, total_stock, stock, version FROM ticket_event WHERE id = ?",
                (rs, rowNum) -> new TicketEvent(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getInt("total_stock"),
                        rs.getInt("stock"),
                        rs.getInt("version")
                ),
                eventId
        );
        return result.stream().findFirst();
    }

    public Optional<TicketEvent> findEventByIdForUpdate(Long eventId) {
        List<TicketEvent> result = jdbcTemplate.query(
                "SELECT id, name, total_stock, stock, version " +
                "FROM ticket_event WHERE id = ? FOR UPDATE",
                (rs, rowNum) -> new TicketEvent(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getInt("total_stock"),
                        rs.getInt("stock"),
                        rs.getInt("version")
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

    // PART 2 unsafe baseline:
    // Java already calculated newStock and writes that value back.
    public void setStockUnsafe(Long eventId, Integer newStock) {
        jdbcTemplate.update(
                "UPDATE ticket_event " +
                "SET stock = ?, version = version + 1 " +
                "WHERE id = ?",
                newStock,
                eventId
        );
    }

    // PART 3: one atomic DB statement.
    public int decrementStockAtomic(Long eventId) {
        return jdbcTemplate.update(
                "UPDATE ticket_event " +
                "SET stock = stock - 1, version = version + 1 " +
                "WHERE id = ? AND stock > 0",
                eventId
        );
    }

    // PART 3: optimistic lock.
    public int updateStockOptimistic(
            Long eventId,
            Integer newStock,
            Integer expectedVersion
    ) {
        return jdbcTemplate.update(
                "UPDATE ticket_event " +
                "SET stock = ?, version = version + 1 " +
                "WHERE id = ? AND version = ? AND stock > 0",
                newStock,
                eventId,
                expectedVersion
        );
    }

    public Long insertOrder(Long eventId, String userId) {
        String sql = "INSERT INTO ticket_order(event_id, user_id, status) " +
                     "VALUES (?, ?, 'SUCCESS')";

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    sql,
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setLong(1, eventId);
            ps.setString(2, userId);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();

        if (key == null) {
            throw new IllegalStateException("建立訂單成功，但取不到 order id");
        }

        return key.longValue();
    }

    public List<TicketOrder> findOrdersByUserId(String userId) {
        return jdbcTemplate.query(
                "SELECT o.id, o.event_id, e.name AS event_name, " +
                "o.user_id, o.status, o.created_at " +
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
